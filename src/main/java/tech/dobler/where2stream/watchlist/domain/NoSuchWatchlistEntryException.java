package tech.dobler.where2stream.watchlist.domain;

import tech.dobler.where2stream.shared.domain.ImdbId;

/** Thrown when a per-title watchlist operation targets a title not on the user's list (mapped to 404). */
public class NoSuchWatchlistEntryException extends RuntimeException {
    public NoSuchWatchlistEntryException(ImdbId imdbId) {
        super("Title not on your watchlist: " + imdbId.value());
    }
}
