package tech.dobler.where2stream.accountaccess.adapter.in.api;

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
import tech.dobler.where2stream.accountaccess.application.command.LanguageUpdateCommand;
import tech.dobler.where2stream.accountaccess.application.command.ShowAgeRatingsUpdateCommand;
import tech.dobler.where2stream.accountaccess.application.command.ShowGermanTitleUpdateCommand;
import tech.dobler.where2stream.accountaccess.application.command.ThemeUpdateCommand;
import tech.dobler.where2stream.accountaccess.application.command.TilesPerRowUpdateCommand;
import tech.dobler.where2stream.accountaccess.application.command.UsernameUpdateCommand;
import tech.dobler.where2stream.accountaccess.application.command.ViewModeUpdateCommand;
import tech.dobler.where2stream.accountaccess.domain.UserPreferences;
import tech.dobler.where2stream.accountaccess.application.UserPreferencesService;
import tech.dobler.where2stream.accountaccess.application.dto.MeDto;
import tech.dobler.where2stream.titlecatalog.port.in.PosterAttributionPort;

import java.util.List;

/** The current principal (username, roles, theme) and updates to the user's own preferences. */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeApiController {

    private final UserPreferencesService userPreferencesService;
    private final PosterAttributionPort posterAttributionPort;

    @GetMapping
    public MeDto me(Authentication authentication) {
        final boolean tmdbAttribution = posterAttributionPort.tmdbAttributionRequired();
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
        userPreferencesService.updateTheme(new ThemeUpdateCommand(authentication.getName(), request.theme()));
    }

    /** Updates the current user's own age-rating-badge preference. */
    @PutMapping("/show-age-ratings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateShowAgeRatings(Authentication authentication, @RequestBody ShowAgeRatingsUpdateRequest request) {
        userPreferencesService.updateShowAgeRatings(
                new ShowAgeRatingsUpdateCommand(authentication.getName(), request.showAgeRatings()));
    }

    /** Updates the current user's own UI language. */
    @PutMapping("/language")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateLanguage(Authentication authentication, @RequestBody LanguageUpdateRequest request) {
        userPreferencesService.updateLanguage(new LanguageUpdateCommand(authentication.getName(), request.language()));
    }

    /** Updates the current user's own German-title preference. */
    @PutMapping("/show-german-title")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateShowGermanTitle(Authentication authentication, @RequestBody ShowGermanTitleUpdateRequest request) {
        userPreferencesService.updateShowGermanTitle(
                new ShowGermanTitleUpdateCommand(authentication.getName(), request.showGermanTitle()));
    }

    /** Updates the current user's own library layout preference (list vs. poster grid). */
    @PutMapping("/view-mode")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateViewMode(Authentication authentication, @RequestBody ViewModeUpdateRequest request) {
        userPreferencesService.updateViewMode(new ViewModeUpdateCommand(authentication.getName(), request.viewMode()));
    }

    /** Updates the current user's own tiles-per-row preference (2-6) for the grid view. */
    @PutMapping("/tiles-per-row")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateTilesPerRow(Authentication authentication, @RequestBody TilesPerRowUpdateRequest request) {
        userPreferencesService.updateTilesPerRow(
                new TilesPerRowUpdateCommand(authentication.getName(), request.tilesPerRow()));
    }

    /**
     * Renames the current user's login username, then invalidates the session so they must log in
     * again as the new name (the current session principal still holds the old one).
     * 409 if taken.
     */
    @PutMapping("/username")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateUsername(Authentication authentication, @RequestBody UsernameUpdateRequest request,
                               HttpServletRequest httpRequest) {
        userPreferencesService.updateUsername(
                new UsernameUpdateCommand(authentication.getName(), request.username()));
        // Force re-authentication as the new name.
        final var session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
