package tech.dobler.werstreamt.api;

/** Body of {@code PUT /api/me/username} (blank → 400). */
public record UsernameUpdateRequest(String username) {
}
