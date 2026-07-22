package tech.dobler.werstreamt.application.dto;

import java.util.List;

/**
 * A user account for the administration UI (no password hash).
 *
 * @param roles    role names (e.g. {@code ["ADMIN","USER"]})
 * @param provider {@code LOCAL} or an OIDC provider like {@code GOOGLE}
 */
public record UserDto(
        String id,
        String username,
        String email,
        boolean enabled,
        List<String> roles,
        String provider
) {
}
