package tech.dobler.werstreamt.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.werstreamt.domain.Language;
import tech.dobler.werstreamt.domain.Role;
import tech.dobler.werstreamt.domain.Theme;
import tech.dobler.werstreamt.domain.ViewMode;
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
    void newAccountsDefaultToSystemThemeEnglishGridAndSixTilesPerRow() {
        final var user = alice();
        assertThat(user)
                .extracting(AppUser::getTheme, AppUser::isShowAgeRatings, AppUser::getLanguage,
                        AppUser::isShowGermanTitle, AppUser::getViewMode, AppUser::getTilesPerRow)
                .containsExactly(Theme.SYSTEM, true, Language.EN, false, ViewMode.GRID, 6);
    }

    @Test
    void preferencesForReturnsEveryStoredPreferenceInOneRead() {
        final var user = alice();
        user.changeTheme(Theme.DARK);
        user.changeShowAgeRatings(false);
        user.changeLanguage(Language.DE);
        user.changeShowGermanTitle(true);
        user.changeViewMode(ViewMode.LIST);
        user.changeTilesPerRow(3);
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThat(service.preferencesFor("alice"))
                .isEqualTo(new UserPreferences(Theme.DARK, false, Language.DE, true, ViewMode.LIST, 3));
    }

    @Test
    void preferencesForFallsBackToDefaultsForAnUnknownUser() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThat(service.preferencesFor("ghost")).isEqualTo(UserPreferences.defaults());
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

    @Test
    void updateShowAgeRatingsChangesAndSavesTheUser() {
        final var user = alice();
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));

        service.updateShowAgeRatings("alice", false);

        assertThat(user.isShowAgeRatings()).isFalse();
        verify(users).save(user);
    }

    @Test
    void updateLanguageChangesAndSavesTheUser() {
        final var user = alice();
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));

        service.updateLanguage("alice", Language.DE);

        assertThat(user.getLanguage()).isEqualTo(Language.DE);
        verify(users).save(user);
    }

    @Test
    void updateShowGermanTitleChangesAndSavesTheUser() {
        final var user = alice();
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));

        service.updateShowGermanTitle("alice", true);

        assertThat(user.isShowGermanTitle()).isTrue();
        verify(users).save(user);
    }

    @Test
    void updateViewModeChangesAndSavesTheUser() {
        final var user = alice();
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));

        service.updateViewMode("alice", ViewMode.LIST);

        assertThat(user.getViewMode()).isEqualTo(ViewMode.LIST);
        verify(users).save(user);
    }

    @Test
    void updateTilesPerRowChangesAndSavesTheUser() {
        final var user = alice();
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));

        service.updateTilesPerRow("alice", 3);

        assertThat(user.getTilesPerRow()).isEqualTo(3);
        verify(users).save(user);
    }

    @Test
    void usernameIsAvailableWhenFreeOrOwnedByTheSameUser() {
        when(users.findByUsername("free")).thenReturn(Optional.empty());
        when(users.findByUsername("alice")).thenReturn(Optional.of(alice()));

        assertThat(service.usernameAvailable("free", "alice")).isTrue();  // free
        assertThat(service.usernameAvailable("alice", "alice")).isTrue(); // their own name
    }

    @Test
    void usernameIsUnavailableWhenTakenBySomeoneElse() {
        final var bob = AppUser.local("bob", "{noop}x", null, Set.of(Role.USER), Instant.parse("2026-01-01T00:00:00Z"));
        when(users.findByUsername("bob")).thenReturn(Optional.of(bob));

        assertThat(service.usernameAvailable("bob", "alice")).isFalse();
    }

    @Test
    void updateUsernameRenamesAndSavesTheUser() {
        final var user = alice();
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));

        service.updateUsername("alice", "alice2");

        assertThat(user.getUsername()).isEqualTo("alice2");
        verify(users).save(user);
    }
}
