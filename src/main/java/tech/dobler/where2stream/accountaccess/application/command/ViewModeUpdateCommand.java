package tech.dobler.where2stream.accountaccess.application.command;

import tech.dobler.where2stream.accountaccess.domain.ViewMode;
import tech.dobler.where2stream.shared.platform.api.ValidationException;

/** Update the current user's library layout preference (list vs. poster grid). */
public record ViewModeUpdateCommand(String username, ViewMode viewMode) {
    public ViewModeUpdateCommand {
        if (viewMode == null) {
            throw new ValidationException("A viewMode is required.");
        }
    }
}
