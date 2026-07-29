package tech.dobler.where2stream.accountaccess.adapter.in.web;

/** Body of {@code PUT /api/me/username} (blank → 400). */
public record UsernameUpdateRequest(String username) {
}
