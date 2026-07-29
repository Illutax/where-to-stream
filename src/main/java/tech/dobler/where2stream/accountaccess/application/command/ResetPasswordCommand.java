package tech.dobler.where2stream.accountaccess.application.command;

import tech.dobler.where2stream.shared.platform.api.ValidationException;

import java.util.UUID;

/** Reset a user's login password (LOCAL accounts only — the service checks the auth provider). */
public record ResetPasswordCommand(UUID id, String newPassword) {
    public ResetPasswordCommand {
        if (newPassword == null || newPassword.isBlank()) {
            throw new ValidationException("password must not be blank");
        }
        newPassword = newPassword.trim();
    }
}
