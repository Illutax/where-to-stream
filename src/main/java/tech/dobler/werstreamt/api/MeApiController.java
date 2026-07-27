package tech.dobler.werstreamt.api;

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
import org.springframework.web.server.ResponseStatusException;
import tech.dobler.werstreamt.application.UserPreferencesService;
import tech.dobler.werstreamt.application.dto.MeDto;
import tech.dobler.werstreamt.configurations.TmdbProperties;
import tech.dobler.werstreamt.domain.Language;
import tech.dobler.werstreamt.domain.Theme;

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
            return new MeDto(false, null, List.of(), false, Theme.SYSTEM, tmdbAttribution, true, Language.EN, false);
        }
        final List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .sorted()
                .toList();
        final var username = authentication.getName();
        final var theme = userPreferencesService.themeFor(username);
        final var showAgeRatings = userPreferencesService.showAgeRatingsFor(username);
        final var language = userPreferencesService.languageFor(username);
        final var showGermanTitle = userPreferencesService.showGermanTitleFor(username);
        return new MeDto(true, username, roles, roles.contains("ADMIN"), theme, tmdbAttribution,
                showAgeRatings, language, showGermanTitle);
    }

    /** Updates the current user's own theme preference. */
    @PutMapping("/theme")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateTheme(Authentication authentication, @RequestBody ThemeUpdateRequest request) {
        if (request == null || request.theme() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A theme is required.");
        }
        userPreferencesService.updateTheme(authentication.getName(), request.theme());
    }

    /** Updates the current user's own age-rating-badge preference. */
    @PutMapping("/show-age-ratings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateShowAgeRatings(Authentication authentication, @RequestBody ShowAgeRatingsUpdateRequest request) {
        if (request == null || request.showAgeRatings() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A showAgeRatings flag is required.");
        }
        userPreferencesService.updateShowAgeRatings(authentication.getName(), request.showAgeRatings());
    }

    /** Updates the current user's own UI language. */
    @PutMapping("/language")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateLanguage(Authentication authentication, @RequestBody LanguageUpdateRequest request) {
        if (request == null || request.language() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A language is required.");
        }
        userPreferencesService.updateLanguage(authentication.getName(), request.language());
    }

    /** Updates the current user's own German-title preference. */
    @PutMapping("/show-german-title")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateShowGermanTitle(Authentication authentication, @RequestBody ShowGermanTitleUpdateRequest request) {
        if (request == null || request.showGermanTitle() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A showGermanTitle flag is required.");
        }
        userPreferencesService.updateShowGermanTitle(authentication.getName(), request.showGermanTitle());
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A username is required.");
        }
        final var currentUsername = authentication.getName();
        final var newUsername = request.username().trim();
        if (!userPreferencesService.usernameAvailable(newUsername, currentUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is already taken.");
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
