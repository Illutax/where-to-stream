package tech.dobler.where2stream.application;

import tech.dobler.where2stream.domain.Language;
import tech.dobler.where2stream.domain.Theme;
import tech.dobler.where2stream.domain.ViewMode;

/**
 * A user's own UI preferences, read in one shot by {@link UserPreferencesService#preferencesFor}.
 * Defaults mirror the ones the individual preferences used to fall back to for an unknown user.
 */
public record UserPreferences(
        Theme theme,
        boolean showAgeRatings,
        Language language,
        boolean showGermanTitle,
        ViewMode viewMode,
        int tilesPerRow
) {
    public static UserPreferences defaults() {
        return new UserPreferences(Theme.SYSTEM, true, Language.EN, false, ViewMode.GRID, 6);
    }
}
