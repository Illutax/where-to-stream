package tech.dobler.werstreamt.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tech.dobler.werstreamt.application.dto.ImdbSearchResultDto;
import tech.dobler.werstreamt.persistence.WatchlistEntryRepository;
import tech.dobler.werstreamt.services.ImdbSuggestionClient;

import java.util.List;
import java.util.UUID;

/**
 * Title search for the navbar search box: delegates the actual IMDb lookup to
 * {@link ImdbSuggestionClient} and enriches each hit with whether it's already on the current
 * user's watchlist. Metadata/poster for a specific hit are never fetched here — the client fetches
 * those lazily through the existing {@code /api/titles/{id}/meta} and {@code /poster} endpoints
 * (DB-cache-first), exactly like every other title in the app.
 */
@Service
@RequiredArgsConstructor
public class ImdbSearchService {

    private final ImdbSuggestionClient imdbSuggestionClient;
    private final WatchlistEntryRepository watchlistEntries;

    public List<ImdbSearchResultDto> search(UUID userId, String query) {
        return imdbSuggestionClient.search(query).stream()
                .map(hit -> new ImdbSearchResultDto(hit.imdbId(), hit.name(), hit.year(),
                        watchlistEntries.existsByUserIdAndImdbId(userId, hit.imdbId())))
                .toList();
    }
}
