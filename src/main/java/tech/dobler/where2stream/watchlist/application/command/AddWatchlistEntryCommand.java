package tech.dobler.where2stream.watchlist.application.command;

import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.shared.platform.api.ValidationException;

import java.util.UUID;

/** Add a single title (found via search) to a user's watchlist. */
public record AddWatchlistEntryCommand(UUID userId, ImdbId imdbId, String name, Integer year) {
    public AddWatchlistEntryCommand {
        if (name == null || name.isBlank() || year == null) {
            throw new ValidationException("A name and year are required.");
        }
    }
}
