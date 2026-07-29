package tech.dobler.where2stream.api;

/** Body of {@code POST /api/watchlist/{imdbId}}: the title as found via search. */
public record AddWatchlistEntryRequest(String name, Integer year) {
}
