package tech.dobler.where2stream.streamingavailability.application.dto;

import tech.dobler.where2stream.streamingavailability.application.AvailabilityFormatter;
import tech.dobler.where2stream.watchlist.domain.ImdbEntry;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.streamingavailability.domain.QueryResult;
import tech.dobler.where2stream.watchlist.domain.WatchlistDate;

/**
 * A title that is (only) purchasable / rentable on a streaming service.
 * The year is rendered for display (the "Not yet released" placeholder comes from
 * {@code ReleaseYear.display()}).
 */
public record PaidEntryDto(
        String name,
        ImdbId imdbId,
        String price,
        WatchlistDate added,
        boolean isRated,
        String year,
        String languages
) {
    public static PaidEntryDto from(QueryResult result, ImdbEntry imdbEntry) {
        final var price = AvailabilityFormatter.prettyPrint(result.availabilities());
        return new PaidEntryDto(imdbEntry.name(), imdbEntry.imdbId(), price, imdbEntry.added(), imdbEntry.isRated(),
                imdbEntry.year().display(), result.languages());
    }
}
