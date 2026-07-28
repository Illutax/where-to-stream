package tech.dobler.werstreamt.application;

import tech.dobler.werstreamt.domain.ImdbId;

/** Thrown when adding a single title that's already on the user's watchlist (mapped to 409). */
public class WatchlistEntryAlreadyExistsException extends RuntimeException {
    public WatchlistEntryAlreadyExistsException(ImdbId imdbId) {
        super("Already on your watchlist: " + imdbId.value());
    }
}
