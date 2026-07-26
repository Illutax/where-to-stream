package tech.dobler.werstreamt.application;

import tech.dobler.werstreamt.domain.ImdbId;

/** Thrown when a per-title watchlist operation targets a title not on the user's list (mapped to 404). */
public class NoSuchWatchlistEntryException extends RuntimeException {
    public NoSuchWatchlistEntryException(ImdbId imdbId) {
        super("Title not on your watchlist: " + imdbId.value());
    }
}
