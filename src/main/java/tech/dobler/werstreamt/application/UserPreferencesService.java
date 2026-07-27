package tech.dobler.werstreamt.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.dobler.werstreamt.domain.Language;
import tech.dobler.werstreamt.domain.Theme;
import tech.dobler.werstreamt.persistence.AppUser;
import tech.dobler.werstreamt.persistence.AppUserRepository;

/**
 * Reads and updates a user's own UI preferences (currently just the colour-scheme theme). Keyed by
 * the authenticated username handed down from the presentation layer — the SPA loads the theme via
 * {@code /api/me} and changes it via {@code PUT /api/me/theme}.
 */
@Service
@RequiredArgsConstructor
public class UserPreferencesService {

    private final AppUserRepository users;

    /** The user's theme preference, or {@link Theme#SYSTEM} if the user is unknown. */
    public Theme themeFor(String username) {
        return users.findByUsername(username).map(AppUser::getTheme).orElse(Theme.SYSTEM);
    }

    @Transactional
    public void updateTheme(String username, Theme theme) {
        final var user = user(username);
        user.changeTheme(theme);
        users.save(user);
    }

    /** Whether the user wants the age-rating badges, defaulting to {@code true} for unknown users. */
    public boolean showAgeRatingsFor(String username) {
        return users.findByUsername(username).map(AppUser::isShowAgeRatings).orElse(true);
    }

    @Transactional
    public void updateShowAgeRatings(String username, boolean show) {
        final var user = user(username);
        user.changeShowAgeRatings(show);
        users.save(user);
    }

    /** The user's UI language, or {@link Language#EN} if the user is unknown. */
    public Language languageFor(String username) {
        return users.findByUsername(username).map(AppUser::getLanguage).orElse(Language.EN);
    }

    @Transactional
    public void updateLanguage(String username, Language language) {
        final var user = user(username);
        user.changeLanguage(language);
        users.save(user);
    }

    /** Whether the user wants German titles, defaulting to {@code false} for unknown users. */
    public boolean showGermanTitleFor(String username) {
        return users.findByUsername(username).map(AppUser::isShowGermanTitle).orElse(false);
    }

    @Transactional
    public void updateShowGermanTitle(String username, boolean show) {
        final var user = user(username);
        user.changeShowGermanTitle(show);
        users.save(user);
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
        final var user = user(currentUsername);
        user.rename(newUsername);
        users.save(user);
    }

    private AppUser user(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + username));
    }
}
