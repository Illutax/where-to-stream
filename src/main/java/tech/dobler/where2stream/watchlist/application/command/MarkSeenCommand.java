package tech.dobler.where2stream.watchlist.application.command;

import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.shared.platform.api.ValidationException;

import java.util.UUID;

/** Toggle a single title's "seen" flag for a user (the lightweight in-app marking). */
public record MarkSeenCommand(UUID userId, ImdbId imdbId, Boolean seen) {
    public MarkSeenCommand {
        if (seen == null) {
            throw new ValidationException("A 'seen' flag is required.");
        }
    }
}
