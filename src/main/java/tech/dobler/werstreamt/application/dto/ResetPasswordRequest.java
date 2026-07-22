package tech.dobler.werstreamt.application.dto;

/** Body of {@code POST /api/admin/users/{id}/password}. */
public record ResetPasswordRequest(String newPassword) {
}
