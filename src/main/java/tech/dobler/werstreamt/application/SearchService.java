package tech.dobler.werstreamt.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.dobler.werstreamt.domain.ImdbEntry;
import tech.dobler.werstreamt.domain.QueryResult;
import tech.dobler.werstreamt.services.ImdbCatalog;
import tech.dobler.werstreamt.services.StreamAvailabilityProvider;
import tech.dobler.werstreamt.services.StreamInfoService;

import java.util.List;
import java.util.Optional;

/**
 * Look up stream availability for a single title, by catalogue id or IMDb id. Shared by the
 * legacy {@code /query} + {@code /search} endpoints and the new {@code /api/search}, so both
 * resolve titles identically.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final ImdbCatalog imdbCatalog;
    private final StreamAvailabilityProvider streamProvider;
    private final StreamInfoService streamInfoService;

    /** Live werstreamt.es query for a catalogue id (bypasses the cache). Empty if the id is unknown. */
    public Optional<List<QueryResult>> liveQueryByCatalogId(int id) {
        final var maybeEntry = imdbCatalog.findById(id);
        logLookup(id, maybeEntry);
        return maybeEntry.map(entry -> streamProvider.query(entry.imdbId()));
    }

    /** Cached resolve for a catalogue id. Empty if the id is unknown or nothing is available. */
    public Optional<List<QueryResult>> resolveByCatalogId(int id) {
        final var maybeEntry = imdbCatalog.findById(id);
        logLookup(id, maybeEntry);
        return maybeEntry.flatMap(entry -> resolveByImdbId(entry.imdbId()));
    }

    /** Cached resolve for an IMDb id. Empty if nothing is available. */
    public Optional<List<QueryResult>> resolveByImdbId(String imdbId) {
        final var searchResult = streamInfoService.resolve(imdbId);
        return searchResult.isEmpty() ? Optional.empty() : Optional.of(searchResult);
    }

    private static void logLookup(int id, Optional<ImdbEntry> maybeEntry) {
        final var name = maybeEntry.map(ImdbEntry::name);
        log.info("Requesting {} with name {}", id, name.orElse("<Not found>"));
    }
}
