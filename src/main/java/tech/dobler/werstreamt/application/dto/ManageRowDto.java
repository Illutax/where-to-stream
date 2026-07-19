package tech.dobler.werstreamt.application.dto;

/** One row of the cache-management table. {@code needsScrape} = currently missing/invalidated cache. */
public record ManageRowDto(
        String imdbId,
        String name,
        boolean isRated,
        boolean needsScrape
) {
}
