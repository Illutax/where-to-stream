package tech.dobler.werstreamt.application.dto;

import java.util.List;

/**
 * The current authenticated principal, for the SPA to drive auth state, guards and admin-only UI.
 *
 * @param authenticated whether a user is authenticated
 * @param username      the login name (local username or OIDC e-mail)
 * @param roles         role names without the {@code ROLE_} prefix (e.g. {@code ["ADMIN","USER"]})
 * @param admin         convenience flag: whether {@code roles} contains {@code ADMIN}
 */
public record MeDto(
        boolean authenticated,
        String username,
        List<String> roles,
        boolean admin
) {
}
