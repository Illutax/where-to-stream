package tech.dobler.where2stream.streamingavailability.application.dto;

import tech.dobler.where2stream.shared.kernel.domain.ImdbId;

import java.time.Instant;

/**
 * One row of the cache-management table. {@code needsScrape} = currently missing/invalidated
 * cache. {@code lastScrapedAt} is the most recent scrape regardless of validity (null = never
 * scraped) — a title can be both {@code needsScrape == true} and have a non-null
 * {@code lastScrapedAt} (an invalidated entry still has a creation time).
 */
public record ManageRowDto(
        ImdbId imdbId,
        String name,
        boolean isRated,
        boolean needsScrape,
        Instant lastScrapedAt
) {
}
