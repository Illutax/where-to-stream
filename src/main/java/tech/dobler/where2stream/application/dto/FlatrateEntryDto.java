package tech.dobler.where2stream.application.dto;

import tech.dobler.where2stream.domain.ImdbEntry;
import tech.dobler.where2stream.domain.ImdbId;
import tech.dobler.where2stream.domain.ReleaseYear;
import tech.dobler.where2stream.domain.WatchlistDate;

/**
 * A title available in the flatrate ("included") of a streaming service. Field names mirror
 * {@link ImdbEntry}.
 */
public record FlatrateEntryDto(
        boolean isRated,
        String name,
        ImdbId imdbId,
        ReleaseYear year,
        WatchlistDate added
) {
    public static FlatrateEntryDto from(ImdbEntry entry) {
        return new FlatrateEntryDto(entry.isRated(), entry.name(), entry.imdbId(), entry.year(), entry.added());
    }
}
