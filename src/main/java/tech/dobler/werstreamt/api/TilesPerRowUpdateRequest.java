package tech.dobler.werstreamt.api;

/** Body of {@code PUT /api/me/tiles-per-row}: tiles per row (2-6) the current user selected. */
public record TilesPerRowUpdateRequest(Integer tilesPerRow) {
}
