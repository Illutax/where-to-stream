package tech.dobler.werstreamt.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
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
            return new MeDto(false, null, List.of(), false, Theme.SYSTEM, tmdbAttribution, true);
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
        return new MeDto(true, username, roles, roles.contains("ADMIN"), theme, tmdbAttribution, showAgeRatings);
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
}
