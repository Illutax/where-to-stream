package tech.dobler.where2stream.shared.platform.web;

/**
 * Everything the ADMIN metrics dashboard shows about this deployment.
 *
 * <p>Deliberately <em>not</em> part of {@link StatusDto}: that record is served unauthenticated by
 * {@code StatusController} for external monitoring, so anything added to it is public. Only the
 * title count is on both.
 *
 * @param users               accounts on this instance
 * @param distinctTitles      distinct titles across every watchlist
 * @param watchlistEntries    watchlist rows in total
 * @param metadataRows        cached IMDb metadata rows
 * @param metadataWithoutData how many of those record "IMDb had nothing"
 * @param posterRows          cached poster rows
 * @param postersWithImage    how many of those actually carry a poster
 * @param availabilityTitles  titles with any availability cache row
 * @param availabilityStale   how many of those need re-scraping
 */
public record InstanceMetricsDto(
        long users,
        long distinctTitles,
        long watchlistEntries,
        long metadataRows,
        long metadataWithoutData,
        long posterRows,
        long postersWithImage,
        long availabilityTitles,
        long availabilityStale
) {
}
