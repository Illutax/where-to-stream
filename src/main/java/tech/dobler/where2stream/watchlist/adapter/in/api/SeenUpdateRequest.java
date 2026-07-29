package tech.dobler.where2stream.watchlist.adapter.in.api;

/** Body of {@code PUT /api/watchlist/{imdbId}/seen}: whether the title is now seen. */
public record SeenUpdateRequest(Boolean seen) {
}
