package tech.dobler.where2stream.api;

/** Body of {@code PUT /api/watchlist/{imdbId}/seen}: whether the title is now seen. */
public record SeenUpdateRequest(Boolean seen) {
}
