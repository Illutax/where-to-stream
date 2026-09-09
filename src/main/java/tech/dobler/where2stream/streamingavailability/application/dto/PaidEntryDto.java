package tech.dobler.where2stream.streamingavailability.application.dto;

import tech.dobler.where2stream.streamingavailability.application.AvailabilityFormatter;
import tech.dobler.where2stream.watchlist.domain.ImdbEntry;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.shared.kernel.domain.ReleaseYear;
import tech.dobler.where2stream.streamingavailability.domain.QueryResult;
import tech.dobler.where2stream.watchlist.domain.WatchlistDate;

/**
 * A title that is (only) purchasable / rentable on a streaming service.
 *
 * <p>The year goes out as a {@link ReleaseYear}, not as display text. It used to be rendered here
 * via {@code display()}, which cost the client the ability to compute with it: a formatted string
 * cannot be turned back into a year without guessing, so anything downstream that needed the
 * number had to treat it as absent (TODO-60). Formatting is the client's job, and it already owns
 * the same placeholder in {@code core/domain.ts}.
 */
public record PaidEntryDto(
        String name,
        ImdbId imdbId,
        String price,
        WatchlistDate added,
        boolean isRated,
        ReleaseYear year,
        String languages
) {
    public static PaidEntryDto from(QueryResult result, ImdbEntry imdbEntry) {
        final var price = AvailabilityFormatter.prettyPrint(result.availabilities());
        return new PaidEntryDto(imdbEntry.name(), imdbEntry.imdbId(), price, imdbEntry.added(), imdbEntry.isRated(),
                imdbEntry.year(), result.languages());
    }
}
