package tech.dobler.where2stream.accountaccess.application;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.dobler.where2stream.accountaccess.application.command.CreateUserCommand;
import tech.dobler.where2stream.accountaccess.application.command.ResetPasswordCommand;
import tech.dobler.where2stream.accountaccess.application.command.UpdateUserCommand;
import tech.dobler.where2stream.accountaccess.application.dto.UserDto;
import tech.dobler.where2stream.accountaccess.domain.AuthProvider;
import tech.dobler.where2stream.accountaccess.domain.Role;
import tech.dobler.where2stream.accountaccess.domain.AppUser;
import tech.dobler.where2stream.accountaccess.port.out.AppUserRepository;
import tech.dobler.where2stream.shared.platform.time.TimeService;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * User administration use cases (list/create/update/delete/reset-password).
 * ADMIN-only (enforced both by URL rules and {@link PreAuthorize} here as defense in depth).
 * Refuses changes that would remove the last enabled admin, to prevent locking the system out.
 */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TimeService timeService;

    @Transactional(readOnly = true)
    public List<UserDto> list() {
        return users.findAll().stream()
                .sorted(Comparator.comparing(AppUser::getUsername))
                .map(UserAdminService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserDto get(UUID id) {
        return toDto(require(id));
    }

    @Transactional
    public UserDto create(CreateUserCommand command) {
        if (users.existsByUsername(command.username())) {
            throw UserManagementException.duplicateUsername(command.username());
        }
        final var saved = users.save(AppUser.local(command.username(), passwordEncoder.encode(command.password()),
                command.email(), EnumSet.copyOf(command.roles()), timeService.now()));
        return toDto(saved);
    }

    @Transactional
    public UserDto update(UpdateUserCommand command) {
        final var user = require(command.id());
        final var newRoles = EnumSet.copyOf(command.roles());
        final boolean losesAdmin = !newRoles.contains(Role.ADMIN) || !command.enabled();
        if (losesAdmin && isLastEnabledAdmin(user)) {
            throw UserManagementException.lastAdmin();
        }
        user.setEmail(command.email());
        user.setRoles(newRoles);
        user.setEnabled(command.enabled());
        return toDto(users.save(user));
    }

    @Transactional
    public void delete(UUID id) {
        final var user = require(id);
        if (isLastEnabledAdmin(user)) {
            throw UserManagementException.lastAdmin();
        }
        users.delete(user);
    }

    @Transactional
    public void resetPassword(ResetPasswordCommand command) {
        final var user = require(command.id());
        if (user.getProvider() != AuthProvider.LOCAL) {
            throw UserManagementException.badRequest("Cannot set a password on a " + user.getProvider() + " account.");
        }
        user.changePassword(passwordEncoder.encode(command.newPassword()));
        users.save(user);
    }

    private AppUser require(UUID id) {
        return users.findById(id).orElseThrow(() -> UserManagementException.notFound(String.valueOf(id)));
    }

    /** True when {@code user} is currently an enabled admin and the only one left. */
    private boolean isLastEnabledAdmin(AppUser user) {
        if (!user.isEnabled() || !user.getRoles().contains(Role.ADMIN)) {
            return false;
        }
        final long enabledAdmins = users.findAll().stream()
                .filter(AppUser::isEnabled)
                .filter(u -> u.getRoles().contains(Role.ADMIN))
                .count();
        return enabledAdmins <= 1;
    }

    private static UserDto toDto(AppUser user) {
        final List<String> roleNames = user.getRoles().stream().map(Role::name).sorted().toList();
        final String id = user.getId() == null ? null : user.getId().toString();
        return new UserDto(id, user.getUsername(), user.getEmail(),
                user.isEnabled(), roleNames, user.getProvider().name());
    }
}
