package tech.dobler.where2stream.accountaccess.adapter.in.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import tech.dobler.where2stream.accountaccess.domain.AuthProvider;
import tech.dobler.where2stream.accountaccess.domain.Role;
import tech.dobler.where2stream.accountaccess.domain.AppUser;
import tech.dobler.where2stream.accountaccess.port.out.AppUserRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private AppUserRepository users;
    @InjectMocks
    private AppUserDetailsService service;

    @Test
    void mapsRolesToAuthoritiesAndEnabledState() {
        when(users.findByUsername("admin")).thenReturn(Optional.of(
                AppUser.local("admin", "{bcrypt}hash", "a@x", Set.of(Role.ADMIN, Role.USER), NOW)));

        final var details = service.loadUserByUsername("admin");

        assertThat(details).extracting(UserDetails::getPassword, UserDetails::isEnabled)
                .containsExactly("{bcrypt}hash", true);
        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void oidcOnlyAccountGetsAnImpossiblePassword() {
        when(users.findByUsername("g@x")).thenReturn(Optional.of(
                AppUser.fromProvider("g@x", "g@x", AuthProvider.GOOGLE, Set.of(Role.USER), NOW)));

        final var details = service.loadUserByUsername("g@x");

        assertThat(details.getPassword()).isEqualTo(AppUserDetailsService.NO_PASSWORD_LOGIN);
    }

    @Test
    void disabledAccountIsReportedDisabled() {
        final var disabled = AppUser.local("bob", "{bcrypt}hash", "b@x", Set.of(Role.USER), NOW);
        disabled.setEnabled(false);
        when(users.findByUsername("bob")).thenReturn(Optional.of(disabled));

        assertThat(service.loadUserByUsername("bob").isEnabled()).isFalse();
    }

    @Test
    void unknownUserThrows() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
