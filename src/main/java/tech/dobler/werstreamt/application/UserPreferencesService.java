package tech.dobler.werstreamt.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.dobler.werstreamt.domain.Language;
import tech.dobler.werstreamt.domain.Theme;
import tech.dobler.werstreamt.domain.ViewMode;
import tech.dobler.werstreamt.persistence.AppUser;
import tech.dobler.werstreamt.persistence.AppUserRepository;

import java.util.function.Consumer;

/**
 * Reads and updates a user's own UI preferences (theme, language, age-rating badges, German
 * titles, library view mode, tiles-per-row). Keyed by the authenticated username handed down from
 * the presentation layer — the SPA loads every preference in one {@code GET /api/me} and changes
 * one at a time via {@code PUT /api/me/<preference>}.
 *
 * <p>One {@link #preferencesFor} read replaces what used to be six separate {@code xFor(username)}
 * getters (one DB round-trip instead of up to six); the six {@code updateX} methods stay separate
 * (each still persists independently, mirroring the six distinct PUT endpoints) but now share the
 * {@link #update} load-mutate-save helper instead of repeating it.
 */
@Service
@RequiredArgsConstructor
public class UserPreferencesService {

    private final AppUserRepository users;

    /** Every preference for {@code username} at once, or the defaults for an unknown user. */
    public UserPreferences preferencesFor(String username) {
        return users.findByUsername(username)
                .map(u -> new UserPreferences(u.getTheme(), u.isShowAgeRatings(), u.getLanguage(),
                        u.isShowGermanTitle(), u.getViewMode(), u.getTilesPerRow()))
                .orElseGet(UserPreferences::defaults);
    }

    @Transactional
    public void updateTheme(String username, Theme theme) {
        update(username, user -> user.changeTheme(theme));
    }

    @Transactional
    public void updateShowAgeRatings(String username, boolean show) {
        update(username, user -> user.changeShowAgeRatings(show));
    }

    @Transactional
    public void updateLanguage(String username, Language language) {
        update(username, user -> user.changeLanguage(language));
    }

    @Transactional
    public void updateShowGermanTitle(String username, boolean show) {
        update(username, user -> user.changeShowGermanTitle(show));
    }

    @Transactional
    public void updateViewMode(String username, ViewMode viewMode) {
        update(username, user -> user.changeViewMode(viewMode));
    }

    @Transactional
    public void updateTilesPerRow(String username, int tilesPerRow) {
        update(username, user -> user.changeTilesPerRow(tilesPerRow));
    }

    /** Whether {@code newUsername} may be taken by {@code currentUsername} (free, or their own name). */
    public boolean usernameAvailable(String newUsername, String currentUsername) {
        return users.findByUsername(newUsername)
                .map(existing -> existing.getUsername().equals(currentUsername))
                .orElse(true);
    }

    /** Renames the login username. The caller must have checked {@link #usernameAvailable}. */
    @Transactional
    public void updateUsername(String currentUsername, String newUsername) {
        update(currentUsername, user -> user.rename(newUsername));
    }

    private void update(String username, Consumer<AppUser> mutator) {
        final var user = user(username);
        mutator.accept(user);
        users.save(user);
    }

    private AppUser user(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + username));
    }
}
