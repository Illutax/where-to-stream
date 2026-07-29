package tech.dobler.where2stream.accountaccess.application.command;

import tech.dobler.where2stream.accountaccess.domain.Language;
import tech.dobler.where2stream.shared.platform.api.ValidationException;

/** Update the current user's UI language preference. */
public record LanguageUpdateCommand(String username, Language language) {
    public LanguageUpdateCommand {
        if (language == null) {
            throw new ValidationException("A language is required.");
        }
    }
}
