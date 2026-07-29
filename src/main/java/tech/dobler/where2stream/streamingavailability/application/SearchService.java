package tech.dobler.where2stream.streamingavailability.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tech.dobler.where2stream.shared.domain.ImdbId;
import tech.dobler.where2stream.streamingavailability.domain.QueryResult;
import tech.dobler.where2stream.streamingavailability.application.StreamInfoService;

import java.util.List;
import java.util.Optional;

/**
 * Look up stream availability for a single title by IMDb id (from the global cache).
 * User-agnostic — the werstreamt.es cache is shared across users.
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    private final StreamInfoService streamInfoService;

    /** Cached resolve for an IMDb id. Empty if nothing is available. */
    public Optional<List<QueryResult>> resolveByImdbId(ImdbId imdbId) {
        final var searchResult = streamInfoService.resolve(imdbId);
        return searchResult.isEmpty() ? Optional.empty() : Optional.of(searchResult);
    }
}
