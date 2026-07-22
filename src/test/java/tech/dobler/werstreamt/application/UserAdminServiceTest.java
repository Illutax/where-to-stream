package tech.dobler.werstreamt.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import tech.dobler.werstreamt.application.dto.CreateUserRequest;
import tech.dobler.werstreamt.application.dto.UpdateUserRequest;
import tech.dobler.werstreamt.domain.AuthProvider;
import tech.dobler.werstreamt.domain.Role;
import tech.dobler.werstreamt.persistence.AppUser;
import tech.dobler.werstreamt.persistence.AppUserRepository;
import tech.dobler.werstreamt.time.TimeService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private AppUserRepository users;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TimeService timeService;
    @InjectMocks
    private UserAdminService service;

    private static AppUser user(String username, Set<Role> roles, boolean enabled, AuthProvider provider) {
        final AppUser u = provider == AuthProvider.LOCAL
                ? AppUser.local(username, "{bcrypt}hash", username + "@x", roles, NOW)
                : AppUser.fromProvider(username, username + "@x", provider, roles, NOW);
        ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
        u.setEnabled(enabled);
        return u;
    }

    @Test
    void createEncodesThePasswordAndPersists() {
        when(timeService.now()).thenReturn(NOW);
        when(users.existsByUsername("bob")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("{bcrypt}enc");
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        final var dto = service.create(new CreateUserRequest("bob", "secret", "bob@x", List.of(Role.USER)));

        assertThat(dto.username()).isEqualTo("bob");
        assertThat(dto.roles()).containsExactly("USER");
        verify(passwordEncoder).encode("secret");
    }

    @Test
    void createRejectsADuplicateUsername() {
        when(users.existsByUsername("bob")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateUserRequest("bob", "secret", null, List.of())))
                .isInstanceOf(UserManagementException.class);
        verify(users, never()).save(any());
    }

    @Test
    void createRejectsABlankPassword() {
        when(users.existsByUsername("bob")).thenReturn(false);

        assertThatThrownBy(() -> service.create(new CreateUserRequest("bob", "  ", null, List.of())))
                .isInstanceOf(UserManagementException.class);
    }

    @Test
    void getUnknownIdThrowsNotFound() {
        final var id = UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(UserManagementException.class);
    }

    @Test
    void updateRefusesToRemoveAdminFromTheLastEnabledAdmin() {
        final var admin = user("admin", Set.of(Role.ADMIN, Role.USER), true, AuthProvider.LOCAL);
        final var id = (UUID) ReflectionTestUtils.getField(admin, "id");
        when(users.findById(id)).thenReturn(Optional.of(admin));
        when(users.findAll()).thenReturn(List.of(admin)); // the only admin

        assertThatThrownBy(() -> service.update(id, new UpdateUserRequest("admin@x", List.of(Role.USER), true)))
                .isInstanceOf(UserManagementException.class);
        verify(users, never()).save(any());
    }

    @Test
    void updateAllowsDemotingWhenAnotherAdminRemains() {
        final var admin = user("admin", Set.of(Role.ADMIN), true, AuthProvider.LOCAL);
        final var other = user("admin2", Set.of(Role.ADMIN), true, AuthProvider.LOCAL);
        final var id = (UUID) ReflectionTestUtils.getField(admin, "id");
        when(users.findById(id)).thenReturn(Optional.of(admin));
        when(users.findAll()).thenReturn(List.of(admin, other));
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        final var dto = service.update(id, new UpdateUserRequest("admin@x", List.of(Role.USER), true));

        assertThat(dto.roles()).containsExactly("USER");
    }

    @Test
    void deleteRefusesToRemoveTheLastEnabledAdmin() {
        final var admin = user("admin", Set.of(Role.ADMIN), true, AuthProvider.LOCAL);
        final var id = (UUID) ReflectionTestUtils.getField(admin, "id");
        when(users.findById(id)).thenReturn(Optional.of(admin));
        when(users.findAll()).thenReturn(List.of(admin));

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(UserManagementException.class);
        verify(users, never()).delete(any());
    }

    @Test
    void resetPasswordRejectsOidcAccounts() {
        final var google = user("g@x", Set.of(Role.USER), true, AuthProvider.GOOGLE);
        final var id = (UUID) ReflectionTestUtils.getField(google, "id");
        when(users.findById(id)).thenReturn(Optional.of(google));

        assertThatThrownBy(() -> service.resetPassword(id, "new"))
                .isInstanceOf(UserManagementException.class);
    }

    @Test
    void resetPasswordEncodesForLocalAccounts() {
        final var local = user("bob", Set.of(Role.USER), true, AuthProvider.LOCAL);
        final var id = (UUID) ReflectionTestUtils.getField(local, "id");
        when(users.findById(id)).thenReturn(Optional.of(local));
        when(passwordEncoder.encode("new")).thenReturn("{bcrypt}new");
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        service.resetPassword(id, "new");

        assertThat(local.getPasswordHash()).isEqualTo("{bcrypt}new");
    }
}
