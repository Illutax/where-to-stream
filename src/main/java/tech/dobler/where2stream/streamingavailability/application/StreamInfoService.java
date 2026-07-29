package tech.dobler.where2stream.streamingavailability.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.dobler.where2stream.streamingavailability.adapter.out.werstreamtes.WerStreamtProperties;
import tech.dobler.where2stream.shared.domain.ImdbId;
import tech.dobler.where2stream.streamingavailability.domain.QueryResult;
import tech.dobler.where2stream.streamingavailability.domain.QueryMeta;
import tech.dobler.where2stream.streamingavailability.port.out.QueryMetaRepository;
import tech.dobler.where2stream.streamingavailability.port.out.StreamAvailabilityProvider;
import tech.dobler.where2stream.streamingavailability.adapter.out.persistence.QueryResultMapper;
import tech.dobler.where2stream.shared.time.TimeService;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamInfoService {
    private final StreamAvailabilityProvider streamProvider;
    private final QueryMetaRepository queryMetaRepository;
    private final WerStreamtProperties properties;
    private final TimeService timeService;

    // NOTE: both public resolve(...) overloads are annotated on purpose.
    // resolve(imdbId) delegates to resolve(imdbId, false) via self-invocation, which bypasses
    // the Spring proxy, so the transaction must already be open when either entry point is the
    // one called through the proxy.
    // This is what makes parallelStream callers (PreCacheController, RefreshController) correct:
    // each parallel call gets its own transaction on its own thread, instead of relying on a
    // controller-level @Transactional that does not span ForkJoinPool worker threads.
    @Transactional
    public List<QueryResult> resolve(ImdbId imdbId, boolean forceRefresh) {
        final var result = queryMetaRepository.findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc(imdbId);
        final var now = timeService.now();
        return result
                // forceRefresh must drop the cached entry so it is refetched (not keep it).
                .filter(queryMeta -> !forceRefresh && isFresh(queryMeta, now))
                .map(StreamInfoService::toQueryResults)
                .orElseGet(() -> fetch(imdbId));
    }

    @Transactional
    public List<QueryResult> resolve(ImdbId imdbId) {
        return resolve(imdbId, false);
    }

    /**
     * Batch variant of {@link #resolve(String)}: reads the cached metadata for all given
     * imdbIds with a single query (instead of one query per id) and only falls back to a
     * remote fetch for the misses.
     * Returns the results keyed by imdbId, preserving the iteration order of {@code imdbIds}.
     */
    @Transactional
    public Map<ImdbId, List<QueryResult>> resolveAll(Collection<ImdbId> imdbIds) {
        final var now = timeService.now();
        final var latestFreshByImdbId = queryMetaRepository.findByImdbIdInAndInvalidatedIsFalse(imdbIds).stream()
                .collect(Collectors.groupingBy(QueryMeta::getImdbId));

        final var resolved = new LinkedHashMap<ImdbId, List<QueryResult>>();
        for (ImdbId imdbId : imdbIds) {
            final var cached = latestFreshByImdbId.getOrDefault(imdbId, List.of()).stream()
                    .max(Comparator.comparing(QueryMeta::getCreationTime))
                    .filter(queryMeta -> isFresh(queryMeta, now))
                    .map(StreamInfoService::toQueryResults);
            resolved.put(imdbId, cached.orElseGet(() -> fetch(imdbId)));
        }
        return resolved;
    }

    public Optional<String> listAllAvailableServiceNames(ImdbId imdbId) {
        return toAvailableServiceNames(resolve(imdbId));
    }

    public static Optional<String> toAvailableServiceNames(List<QueryResult> queryResults) {
        if (queryResults.isEmpty()) return Optional.empty();
        return Optional.of(queryResults.stream()
                .map(QueryResult::label)
                .collect(Collectors.joining(", ")));
    }

    private boolean isFresh(QueryMeta queryMeta, Instant now) {
        final var threshold = queryMeta.getCreationTime().plusSeconds(TimeUnit.DAYS.toSeconds(properties.invalidate().afterDays()));
        final var passedThreshold = threshold.isBefore(now);
        if (passedThreshold) {
            log.warn("Entry with id {} passed threshold {} > {}", queryMeta.getImdbId(), threshold, now);
        }
        return !passedThreshold;
    }

    private static List<QueryResult> toQueryResults(QueryMeta queryMeta) {
        return queryMeta.getQueries().stream()
                .map(QueryResultMapper.INSTANCE::dtoToEntity)
                .toList();
    }

    private List<QueryResult> fetch(ImdbId imdbId) {
        log.info("Fetching imdb entries for imdbId {}", imdbId);
        // The werstreamt.es cache is global (per imdbId), so scrape directly — no watchlist gate.
        final var queryResults = streamProvider.query(imdbId);
        final var list = queryResults.stream().map(QueryResultMapper.INSTANCE::entityToDto).toList();
        queryMetaRepository.save(QueryMeta.of(imdbId, timeService.now(), list));
        return queryResults;
    }
}
