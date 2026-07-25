package tech.dobler.werstreamt.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tech.dobler.werstreamt.persistence.AppUser;
import tech.dobler.werstreamt.persistence.AppUserRepository;

import java.util.UUID;

/**
 * Resolves an authenticated username to its user id. This is the one place the application layer
 * bridges "who is logged in" (a username string handed down from the presentation layer) to the
 * {@code userId} used to scope watchlist queries — the layers below never see Spring Security.
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final AppUserRepository users;

    public UUID resolveId(String username) {
        return users.findByUsername(username)
                .map(AppUser::getId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + username));
    }
}
