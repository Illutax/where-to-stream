package tech.dobler.where2stream.application.dto;

import tech.dobler.where2stream.shared.domain.ImdbId;
import tech.dobler.where2stream.shared.domain.ReleaseYear;
import tech.dobler.where2stream.watchlist.domain.WatchlistDate;

/**
 * One row of the catalogue overview page, exposed by the {@code /api/catalog} JSON endpoint.
 *
 * @param services comma-separated list of streaming services the title is available on, or
 *                 {@code null} when it is not available anywhere (rendered as "N/A").
 */
public record OverviewEntryDto(
        boolean isRated,
        String name,
        ImdbId imdbId,
        ReleaseYear year,
        WatchlistDate added,
        String services
) {
}
