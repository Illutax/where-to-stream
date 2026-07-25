package tech.dobler.werstreamt.application.dto;

import tech.dobler.werstreamt.domain.ImdbId;

/** One row of the cache-management table. {@code needsScrape} = currently missing/invalidated cache. */
public record ManageRowDto(
        ImdbId imdbId,
        String name,
        boolean isRated,
        boolean needsScrape
) {
}
