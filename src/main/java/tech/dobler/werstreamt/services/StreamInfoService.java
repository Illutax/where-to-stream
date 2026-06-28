package tech.dobler.werstreamt.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.dobler.werstreamt.configurations.WerStreamtProperties;
import tech.dobler.werstreamt.domain.QueryResult;
import tech.dobler.werstreamt.persistence.QueryMeta;
import tech.dobler.werstreamt.persistence.QueryMetaRepository;
import tech.dobler.werstreamt.services.mappers.QueryResultMapper;

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
    private final ImdbCatalog imdbCatalog;
    private final QueryMetaRepository queryMetaRepository;
    private final WerStreamtProperties properties;

    // NOTE: both public resolve(...) overloads are annotated on purpose. resolve(imdbId)
    // delegates to resolve(imdbId, false) via self-invocation, which bypasses the Spring
    // proxy, so the transaction must already be open when either entry point is the one
    // called through the proxy. This is what makes parallelStream callers (PreCacheController,
    // RefreshController) correct: each parallel call gets its own transaction on its own
    // thread, instead of relying on a controller-level @Transactional that does not span
    // ForkJoinPool worker threads.
    @Transactional
    public List<QueryResult> resolve(String imdbId, boolean forceRefresh) {
        final var result = queryMetaRepository.findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc(imdbId);
        final var now = Instant.now();
        return result
                // forceRefresh must drop the cached entry so it is refetched (not keep it).
                .filter(queryMeta -> !forceRefresh && isFresh(queryMeta, now))
                .map(StreamInfoService::toQueryResults)
                .orElseGet(() -> fetch(imdbId));
    }

    @Transactional
    public List<QueryResult> resolve(String imdbId) {
        return resolve(imdbId, false);
    }

    /**
     * Batch variant of {@link #resolve(String)}: reads the cached metadata for all given
     * imdbIds with a single query (instead of one query per id) and only falls back to a
     * remote fetch for the misses. Returns the results keyed by imdbId, preserving the
     * iteration order of {@code imdbIds}.
     */
    @Transactional
    public Map<String, List<QueryResult>> resolveAll(Collection<String> imdbIds) {
        final var now = Instant.now();
        final var latestFreshByImdbId = queryMetaRepository.findByImdbIdInAndInvalidatedIsFalse(imdbIds).stream()
                .collect(Collectors.groupingBy(QueryMeta::getImdbId));

        final var resolved = new LinkedHashMap<String, List<QueryResult>>();
        for (String imdbId : imdbIds) {
            final var cached = latestFreshByImdbId.getOrDefault(imdbId, List.of()).stream()
                    .max(Comparator.comparing(QueryMeta::getCreationTime))
                    .filter(queryMeta -> isFresh(queryMeta, now))
                    .map(StreamInfoService::toQueryResults);
            resolved.put(imdbId, cached.orElseGet(() -> fetch(imdbId)));
        }
        return resolved;
    }

    public Optional<String> listAllAvailableServiceNames(String imdbId) {
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

    private List<QueryResult> fetch(String imdbId) {
        log.info("Fetching imdb entries for imdbId {}", imdbId);
        final var queryResults = imdbCatalog.findByImdb(imdbId)
                .map(entry -> streamProvider.query(entry.imdbId()))
                .orElse(List.of());
        final var list = queryResults.stream().map(QueryResultMapper.INSTANCE::entityToDto).toList();
        queryMetaRepository.save(QueryMeta.of(imdbId, Instant.now(), list));
        return queryResults;
    }
}
