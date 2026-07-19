package tech.dobler.werstreamt.application.dto;

/**
 * One row of the catalogue overview page. View-agnostic: consumed both by the Thymeleaf
 * {@code index} template and the {@code /api/catalog} JSON endpoint.
 *
 * @param services comma-separated list of streaming services the title is available on, or
 *                 {@code null} when it is not available anywhere (rendered as "N/A").
 */
public record OverviewEntryDto(
        boolean isRated,
        String name,
        String imdbId,
        int year,
        String added,
        String services
) {
}
