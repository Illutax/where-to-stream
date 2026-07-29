package tech.dobler.where2stream.accountaccess.application.command;

import tech.dobler.where2stream.shared.platform.api.ValidationException;

/** Update the current user's age-rating-badge preference (Boolean so a missing value is a 400). */
public record ShowAgeRatingsUpdateCommand(String username, Boolean showAgeRatings) {
    public ShowAgeRatingsUpdateCommand {
        if (showAgeRatings == null) {
            throw new ValidationException("A showAgeRatings flag is required.");
        }
    }
}
