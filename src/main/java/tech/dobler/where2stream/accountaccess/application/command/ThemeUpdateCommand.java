package tech.dobler.where2stream.accountaccess.application.command;

import tech.dobler.where2stream.accountaccess.domain.Theme;
import tech.dobler.where2stream.shared.platform.api.ValidationException;

/** Update the current user's colour-scheme preference. */
public record ThemeUpdateCommand(String username, Theme theme) {
    public ThemeUpdateCommand {
        if (theme == null) {
            throw new ValidationException("A theme is required.");
        }
    }
}
