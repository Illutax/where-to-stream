package tech.dobler.where2stream.accountaccess.application.command;

import tech.dobler.where2stream.shared.platform.api.ValidationException;

/** Update the current user's German-title preference (Boolean so a missing value is a 400). */
public record ShowGermanTitleUpdateCommand(String username, Boolean showGermanTitle) {
    public ShowGermanTitleUpdateCommand {
        if (showGermanTitle == null) {
            throw new ValidationException("A showGermanTitle flag is required.");
        }
    }
}
