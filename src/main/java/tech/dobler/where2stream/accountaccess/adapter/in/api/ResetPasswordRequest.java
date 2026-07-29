package tech.dobler.where2stream.accountaccess.adapter.in.api;

/** Body of {@code POST /api/admin/users/{id}/password}. */
public record ResetPasswordRequest(String newPassword) {
}
