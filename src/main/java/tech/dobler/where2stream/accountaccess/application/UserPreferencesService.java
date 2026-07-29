package tech.dobler.where2stream.accountaccess.application;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.dobler.where2stream.accountaccess.application.command.LanguageUpdateCommand;
import tech.dobler.where2stream.accountaccess.application.command.ShowAgeRatingsUpdateCommand;
import tech.dobler.where2stream.accountaccess.application.command.ShowGermanTitleUpdateCommand;
import tech.dobler.where2stream.accountaccess.application.command.ThemeUpdateCommand;
import tech.dobler.where2stream.accountaccess.application.command.TilesPerRowUpdateCommand;
import tech.dobler.where2stream.accountaccess.application.command.UsernameUpdateCommand;
import tech.dobler.where2stream.accountaccess.application.command.ViewModeUpdateCommand;
import tech.dobler.where2stream.accountaccess.domain.UserPreferences;
import tech.dobler.where2stream.accountaccess.domain.AppUser;
import tech.dobler.where2stream.accountaccess.port.out.AppUserRepository;
import tech.dobler.where2stream.shared.platform.api.ValidationException;

import java.util.function.Consumer;

/**
 * Reads and updates a user's own UI preferences (theme, language, age-rating badges, German
 * titles, library view mode, tiles-per-row).
 * Keyed by the authenticated username handed down from the presentation layer — the SPA loads
 * every preference in one {@code GET /api/me}
 * and changes one at a time via {@code PUT /api/me/<preference>}.
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
    public void updateTheme(ThemeUpdateCommand command) {
        update(command.username(), user -> user.changeTheme(command.theme()));
    }

    @Transactional
    public void updateShowAgeRatings(ShowAgeRatingsUpdateCommand command) {
        update(command.username(), user -> user.changeShowAgeRatings(command.showAgeRatings()));
    }

    @Transactional
    public void updateLanguage(LanguageUpdateCommand command) {
        update(command.username(), user -> user.changeLanguage(command.language()));
    }

    @Transactional
    public void updateShowGermanTitle(ShowGermanTitleUpdateCommand command) {
        update(command.username(), user -> user.changeShowGermanTitle(command.showGermanTitle()));
    }

    @Transactional
    public void updateViewMode(ViewModeUpdateCommand command) {
        update(command.username(), user -> user.changeViewMode(command.viewMode()));
    }

    @Transactional
    public void updateTilesPerRow(TilesPerRowUpdateCommand command) {
        update(command.username(), user -> user.changeTilesPerRow(command.tilesPerRow()));
    }

    /** Renames the login username. 409 if {@code newUsername} is already taken by someone else. */
    @Transactional
    public void updateUsername(UsernameUpdateCommand command) {
        final boolean available = users.findByUsername(command.newUsername())
                .map(existing -> existing.getUsername().equals(command.currentUsername()))
                .orElse(true);
        if (!available) {
            throw new ValidationException(HttpStatus.CONFLICT, "That username is already taken.");
        }
        update(command.currentUsername(), user -> user.rename(command.newUsername()));
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
