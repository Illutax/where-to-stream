package tech.dobler.werstreamt.application.dto;

import tech.dobler.werstreamt.domain.Language;
import tech.dobler.werstreamt.domain.Theme;

import java.util.List;

/**
 * The current authenticated principal, for the SPA to drive auth state, guards and admin-only UI.
 *
 * @param authenticated whether a user is authenticated
 * @param username      the login name (local username or OIDC e-mail)
 * @param roles         role names without the {@code ROLE_} prefix (e.g. {@code ["ADMIN","USER"]})
 * @param admin           convenience flag: whether {@code roles} contains {@code ADMIN}
 * @param theme           the user's UI colour-scheme preference ({@code SYSTEM} when anonymous)
 * @param tmdbAttribution whether TMDB is the active poster source (drives the TMDB footer)
 * @param showAgeRatings  whether the user sees the FSK age-rating badges (on by default)
 * @param language        the user's UI language ({@code EN} when anonymous)
 * @param showGermanTitle whether film titles are shown in German where available (off by default)
 */
public record MeDto(
        boolean authenticated,
        String username,
        List<String> roles,
        boolean admin,
        Theme theme,
        boolean tmdbAttribution,
        boolean showAgeRatings,
        Language language,
        boolean showGermanTitle
) {
}
