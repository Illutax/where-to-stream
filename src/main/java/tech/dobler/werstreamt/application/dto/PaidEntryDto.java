package tech.dobler.werstreamt.application.dto;

import tech.dobler.werstreamt.application.AvailabilityFormatter;
import tech.dobler.werstreamt.domain.ImdbEntry;
import tech.dobler.werstreamt.domain.QueryResult;

/**
 * A title that is (only) purchasable / rentable on a streaming service. Field names and the
 * "Not yet released" year rule are preserved from the former
 * {@code DataAggregateController.PaidDto} so the Google Play / Amazon templates render it
 * unchanged.
 */
public record PaidEntryDto(
        String name,
        String imdbId,
        String price,
        String added,
        boolean isRated,
        String year,
        String languages
) {
    public static PaidEntryDto from(QueryResult result, ImdbEntry imdbEntry) {
        final var price = AvailabilityFormatter.prettyPrint(result.availabilities());
        final var year = imdbEntry.year() == 0
                ? "Not yet released"
                : String.valueOf(imdbEntry.year());
        return new PaidEntryDto(imdbEntry.name(), imdbEntry.imdbId(), price, imdbEntry.added(), imdbEntry.isRated(),
                year, result.languages());
    }
}
