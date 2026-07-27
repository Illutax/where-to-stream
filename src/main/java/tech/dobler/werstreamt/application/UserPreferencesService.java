package tech.dobler.werstreamt.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    private AppUser user(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + username));
    }
}
