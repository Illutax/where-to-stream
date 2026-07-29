package tech.dobler.where2stream.accountaccess.application.command;

import tech.dobler.where2stream.shared.platform.api.ValidationException;

/**
 * Rename the current user's login username.
 * {@code newUsername} is trimmed here; whether it's actually available is a business rule
 * ({@link tech.dobler.where2stream.accountaccess.application.UserPreferencesService} checks the
 * database), not a structural check this record can make on its own.
 */
public record UsernameUpdateCommand(String currentUsername, String newUsername) {
    public UsernameUpdateCommand {
        if (newUsername == null || newUsername.isBlank()) {
            throw new ValidationException("A username is required.");
        }
        newUsername = newUsername.trim();
    }
}
