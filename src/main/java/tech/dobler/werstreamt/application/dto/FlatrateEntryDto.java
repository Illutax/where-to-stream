package tech.dobler.werstreamt.application.dto;

import tech.dobler.werstreamt.domain.ImdbEntry;
import tech.dobler.werstreamt.domain.ImdbId;

/**
 * A title available in the flatrate ("included") of a streaming service. Field names mirror
 * {@link ImdbEntry}.
 */
public record FlatrateEntryDto(
        boolean isRated,
        String name,
        ImdbId imdbId,
        int year,
        String added
) {
    public static FlatrateEntryDto from(ImdbEntry entry) {
        return new FlatrateEntryDto(entry.isRated(), entry.name(), entry.imdbId(), entry.year(), entry.added());
    }
}
