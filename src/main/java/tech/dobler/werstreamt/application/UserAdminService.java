package tech.dobler.werstreamt.application;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.dobler.werstreamt.application.dto.CreateUserRequest;
import tech.dobler.werstreamt.application.dto.UpdateUserRequest;
import tech.dobler.werstreamt.application.dto.UserDto;
import tech.dobler.werstreamt.domain.AuthProvider;
import tech.dobler.werstreamt.domain.Role;
import tech.dobler.werstreamt.persistence.AppUser;
import tech.dobler.werstreamt.persistence.AppUserRepository;
import tech.dobler.werstreamt.time.TimeService;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * User administration use cases (list/create/update/delete/reset-password). ADMIN-only (enforced
 * both by URL rules and {@link PreAuthorize} here as defense in depth). Refuses changes that would
 * remove the last enabled admin, to prevent locking the system out.
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
    public UserDto create(CreateUserRequest request) {
        final var username = requireText(request.username(), "username");
        if (users.existsByUsername(username)) {
            throw UserManagementException.duplicateUsername(username);
        }
        final var password = requireText(request.password(), "password");
        final var saved = users.save(AppUser.local(
                username, passwordEncoder.encode(password), request.email(), roles(request.roles()), timeService.now()));
        return toDto(saved);
    }

    @Transactional
    public UserDto update(UUID id, UpdateUserRequest request) {
        final var user = require(id);
        final var newRoles = roles(request.roles());
        final boolean losesAdmin = !newRoles.contains(Role.ADMIN) || !request.enabled();
        if (losesAdmin && isLastEnabledAdmin(user)) {
            throw UserManagementException.lastAdmin();
        }
        user.setEmail(request.email());
        user.setRoles(newRoles);
        user.setEnabled(request.enabled());
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
    public void resetPassword(UUID id, String newPassword) {
        final var user = require(id);
        if (user.getProvider() != AuthProvider.LOCAL) {
            throw UserManagementException.badRequest("Cannot set a password on a " + user.getProvider() + " account.");
        }
        user.changePassword(passwordEncoder.encode(requireText(newPassword, "password")));
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

    private static Set<Role> roles(List<Role> roles) {
        return roles == null || roles.isEmpty() ? EnumSet.of(Role.USER) : EnumSet.copyOf(roles);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw UserManagementException.badRequest(field + " must not be blank");
        }
        return value.trim();
    }

    private static UserDto toDto(AppUser user) {
        final List<String> roleNames = user.getRoles().stream().map(Role::name).sorted().toList();
        final String id = user.getId() == null ? null : user.getId().toString();
        return new UserDto(id, user.getUsername(), user.getEmail(),
                user.isEnabled(), roleNames, user.getProvider().name());
    }
}
