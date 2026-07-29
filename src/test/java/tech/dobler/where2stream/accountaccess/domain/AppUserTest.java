package tech.dobler.where2stream.accountaccess.domain;

import org.junit.jupiter.api.Test;
import tech.dobler.where2stream.accountaccess.domain.AuthProvider;
import tech.dobler.where2stream.accountaccess.domain.Role;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AppUserTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void localBuildsAPasswordBearingLocalAccount() {
        final var user = AppUser.local("bob", "{bcrypt}hash", "bob@x", Set.of(Role.USER), NOW);

        assertThat(user)
                .extracting(AppUser::getUsername, AppUser::getPasswordHash, AppUser::getProvider, AppUser::isEnabled)
                .containsExactly("bob", "{bcrypt}hash", AuthProvider.LOCAL, true);
    }

    @Test
    void fromProviderBuildsAPasswordlessOidcAccount() {
        final var user = AppUser.fromProvider("bob@x", "bob@x", AuthProvider.GOOGLE, Set.of(Role.USER), NOW);

        assertThat(user).extracting(AppUser::getPasswordHash, AppUser::getProvider)
                .containsExactly(null, AuthProvider.GOOGLE);
    }

    @Test
    void anEmptyRoleSetDefaultsToUser() {
        final var user = AppUser.local("bob", "{bcrypt}hash", "bob@x", Set.of(), NOW);

        assertThat(user.getRoles()).containsExactly(Role.USER);
    }

    @Test
    void setRolesWithAnEmptySetResetsToUser() {
        final var user = AppUser.local("bob", "{bcrypt}hash", "bob@x", Set.of(Role.ADMIN), NOW);

        user.setRoles(Set.of());

        assertThat(user.getRoles()).containsExactly(Role.USER);
    }

    @Test
    void setRolesReplacesTheExistingRoles() {
        final var user = AppUser.local("bob", "{bcrypt}hash", "bob@x", Set.of(Role.USER), NOW);

        user.setRoles(EnumSet.of(Role.USER, Role.ADMIN));

        assertThat(user.getRoles()).containsExactlyInAnyOrder(Role.USER, Role.ADMIN);
    }

    @Test
    void changePasswordReplacesTheHash() {
        final var user = AppUser.local("bob", "{bcrypt}old", "bob@x", Set.of(Role.USER), NOW);

        user.changePassword("{bcrypt}new");

        assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}new");
    }
}
