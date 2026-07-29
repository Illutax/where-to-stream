package tech.dobler.where2stream.application;

import tech.dobler.where2stream.domain.ImdbId;

/** Thrown when adding a single title that's already on the user's watchlist (mapped to 409). */
public class WatchlistEntryAlreadyExistsException extends RuntimeException {
    public WatchlistEntryAlreadyExistsException(ImdbId imdbId) {
        super("Already on your watchlist: " + imdbId.value());
    }
}
