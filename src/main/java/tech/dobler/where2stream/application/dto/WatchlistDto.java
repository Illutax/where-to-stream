package tech.dobler.where2stream.application.dto;

import java.time.Instant;

/**
 * Status of a user's watchlist.
 *
 * @param count          number of titles on the watchlist
 * @param lastImportedAt when the most recent title was imported, or {@code null} if empty
 */
public record WatchlistDto(
        long count,
        Instant lastImportedAt
) {
}
