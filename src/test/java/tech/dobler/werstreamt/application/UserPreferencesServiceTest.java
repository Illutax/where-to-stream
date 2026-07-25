package tech.dobler.werstreamt.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.werstreamt.domain.Role;
import tech.dobler.werstreamt.domain.Theme;
import tech.dobler.werstreamt.persistence.AppUser;
import tech.dobler.werstreamt.persistence.AppUserRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPreferencesServiceTest {

    @Mock
    private AppUserRepository users;
    @InjectMocks
    private UserPreferencesService service;

    private static AppUser alice() {
        return AppUser.local("alice", "{noop}x", "a@x", Set.of(Role.USER), Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void themeForReturnsTheStoredThemeOfAKnownUser() {
        final var user = alice();
        user.changeTheme(Theme.DARK);
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThat(service.themeFor("alice")).isEqualTo(Theme.DARK);
    }

    @Test
    void themeForFallsBackToSystemForAnUnknownUser() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThat(service.themeFor("ghost")).isEqualTo(Theme.SYSTEM);
    }

    @Test
    void newAccountsDefaultToTheSystemTheme() {
        assertThat(alice().getTheme()).isEqualTo(Theme.SYSTEM);
    }

    @Test
    void updateThemeChangesAndSavesTheUser() {
        final var user = alice();
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));

        service.updateTheme("alice", Theme.LIGHT);

        assertThat(user.getTheme()).isEqualTo(Theme.LIGHT);
        verify(users).save(user);
    }

    @Test
    void updateThemeThrowsForAnUnknownUser() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateTheme("ghost", Theme.DARK))
                .isInstanceOf(IllegalStateException.class);
    }
}
