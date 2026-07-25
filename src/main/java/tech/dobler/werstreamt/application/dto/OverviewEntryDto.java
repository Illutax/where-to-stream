package tech.dobler.werstreamt.application.dto;

import tech.dobler.werstreamt.domain.ImdbId;

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
        int year,
        String added,
        String services
) {
}
