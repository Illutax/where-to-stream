package tech.dobler.where2stream.accountaccess.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.dobler.where2stream.accountaccess.domain.UserPreferences;
import tech.dobler.where2stream.accountaccess.application.UserPreferencesService;
import tech.dobler.where2stream.shared.api.ValidationException;
import tech.dobler.where2stream.accountaccess.application.dto.MeDto;
import tech.dobler.where2stream.configurations.TmdbProperties;

import java.util.List;

/** The current principal (username, roles, theme) and updates to the user's own preferences. */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeApiController {

    private final UserPreferencesService userPreferencesService;
    private final TmdbProperties tmdbProperties;

    @GetMapping
    public MeDto me(Authentication authentication) {
        final boolean tmdbAttribution = tmdbProperties.active();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return toDto(false, null, List.of(), tmdbAttribution, UserPreferences.defaults());
        }
        final List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .sorted()
                .toList();
        final var username = authentication.getName();
        final var prefs = userPreferencesService.preferencesFor(username);
        return toDto(true, username, roles, tmdbAttribution, prefs);
    }

    private static MeDto toDto(boolean authenticated, String username, List<String> roles,
                               boolean tmdbAttribution, UserPreferences prefs) {
        return new MeDto(authenticated, username, roles, roles.contains("ADMIN"), prefs.theme(), tmdbAttribution,
                prefs.showAgeRatings(), prefs.language(), prefs.showGermanTitle(), prefs.viewMode(), prefs.tilesPerRow());
    }

    /** Updates the current user's own theme preference. */
    @PutMapping("/theme")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateTheme(Authentication authentication, @RequestBody ThemeUpdateRequest request) {
        if (request == null || request.theme() == null) {
            throw new ValidationException("A theme is required.");
        }
        userPreferencesService.updateTheme(authentication.getName(), request.theme());
    }

    /** Updates the current user's own age-rating-badge preference. */
    @PutMapping("/show-age-ratings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateShowAgeRatings(Authentication authentication, @RequestBody ShowAgeRatingsUpdateRequest request) {
        if (request == null || request.showAgeRatings() == null) {
            throw new ValidationException("A showAgeRatings flag is required.");
        }
        userPreferencesService.updateShowAgeRatings(authentication.getName(), request.showAgeRatings());
    }

    /** Updates the current user's own UI language. */
    @PutMapping("/language")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateLanguage(Authentication authentication, @RequestBody LanguageUpdateRequest request) {
        if (request == null || request.language() == null) {
            throw new ValidationException("A language is required.");
        }
        userPreferencesService.updateLanguage(authentication.getName(), request.language());
    }

    /** Updates the current user's own German-title preference. */
    @PutMapping("/show-german-title")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateShowGermanTitle(Authentication authentication, @RequestBody ShowGermanTitleUpdateRequest request) {
        if (request == null || request.showGermanTitle() == null) {
            throw new ValidationException("A showGermanTitle flag is required.");
        }
        userPreferencesService.updateShowGermanTitle(authentication.getName(), request.showGermanTitle());
    }

    /** Updates the current user's own library layout preference (list vs. poster grid). */
    @PutMapping("/view-mode")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateViewMode(Authentication authentication, @RequestBody ViewModeUpdateRequest request) {
        if (request == null || request.viewMode() == null) {
            throw new ValidationException("A viewMode is required.");
        }
        userPreferencesService.updateViewMode(authentication.getName(), request.viewMode());
    }

    /** Updates the current user's own tiles-per-row preference (2-6) for the grid view. */
    @PutMapping("/tiles-per-row")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateTilesPerRow(Authentication authentication, @RequestBody TilesPerRowUpdateRequest request) {
        if (request == null || request.tilesPerRow() == null) {
            throw new ValidationException("A tilesPerRow is required.");
        }
        if (request.tilesPerRow() < 2 || request.tilesPerRow() > 6) {
            throw new ValidationException("tilesPerRow must be between 2 and 6.");
        }
        userPreferencesService.updateTilesPerRow(authentication.getName(), request.tilesPerRow());
    }

    /**
     * Renames the current user's login username, then invalidates the session so they must log in
     * again as the new name (the current session principal still holds the old one). 409 if taken.
     */
    @PutMapping("/username")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateUsername(Authentication authentication, @RequestBody UsernameUpdateRequest request,
                               HttpServletRequest httpRequest) {
        if (request == null || request.username() == null || request.username().isBlank()) {
            throw new ValidationException("A username is required.");
        }
        final var currentUsername = authentication.getName();
        final var newUsername = request.username().trim();
        if (!userPreferencesService.usernameAvailable(newUsername, currentUsername)) {
            throw new ValidationException(HttpStatus.CONFLICT, "That username is already taken.");
        }
        userPreferencesService.updateUsername(currentUsername, newUsername);
        // Force re-authentication as the new name.
        final var session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
