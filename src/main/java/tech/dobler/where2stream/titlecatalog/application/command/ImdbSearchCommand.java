package tech.dobler.where2stream.titlecatalog.application.command;

import tech.dobler.where2stream.shared.platform.api.ValidationException;

import java.util.UUID;

/** Free-text title search against IMDb for a given user (the navbar search box). */
public record ImdbSearchCommand(UUID userId, String query) {
    public ImdbSearchCommand {
        if (query == null || query.isBlank()) {
            throw new ValidationException("A search query is required.");
        }
    }
}
