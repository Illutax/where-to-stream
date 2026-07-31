package tech.dobler.where2stream.streamingavailability.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.dobler.where2stream.streamingavailability.adapter.out.werstreamtes.WerStreamtProperties;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.streamingavailability.domain.QueryResult;
import tech.dobler.where2stream.streamingavailability.domain.QueryMeta;
import tech.dobler.where2stream.streamingavailability.port.out.QueryMetaRepository;
import tech.dobler.where2stream.streamingavailability.port.out.StreamAvailabilityPort;
import tech.dobler.where2stream.streamingavailability.adapter.out.persistence.QueryResultMapper;
import tech.dobler.where2stream.shared.platform.concurrency.RefreshInFlightTracker;
import tech.dobler.where2stream.shared.platform.time.TimeService;
import tech.dobler.where2stream.shared.platform.observability.LogExecutionTime;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StreamInfoService {
    private final StreamAvailabilityPort streamProvider;
    private final QueryMetaRepository queryMetaRepository;
    private final WerStreamtProperties properties;
    private final TimeService timeService;
    private final RefreshInFlightTracker refreshInFlightTracker;
    /**
     * The bean's own proxy: {@link #resolveAll} fetches misses in parallel through it, so each
     * fetch opens its own transaction on its own thread and no connection is held across the
     * network call (same connection discipline as {@code PosterService}/{@code TitleMetaService},
     * ADR-0011) — and (ADR-0016) so the {@code @Async} background refresh below is actually
     * dispatched to the executor instead of running inline (self-invocation bypasses the proxy
     * for {@code @Async} exactly as it does for {@code @Transactional}).
     */
    private final ObjectProvider<StreamInfoService> self;

    public StreamInfoService(StreamAvailabilityPort streamProvider, QueryMetaRepository queryMetaRepository,
                             WerStreamtProperties properties, TimeService timeService,
                             RefreshInFlightTracker refreshInFlightTracker,
                             ObjectProvider<StreamInfoService> self) {
        this.streamProvider = streamProvider;
        this.queryMetaRepository = queryMetaRepository;
        this.properties = properties;
        this.timeService = timeService;
        this.refreshInFlightTracker = refreshInFlightTracker;
        this.self = self;
    }

    // NOTE: both public resolve(...) overloads are annotated on purpose.
    // resolve(imdbId) delegates to resolve(imdbId, false) via self-invocation, which bypasses
    // the Spring proxy, so the transaction must already be open when either entry point is the
    // one called through the proxy.
    // This is what makes parallelStream callers correct — RefreshService/PreCacheService calling
    // in from outside, and resolveAll below calling back in through `self` — each parallel call
    // gets its own transaction on its own thread, instead of relying on a wrapping @Transactional
    // that does not span ForkJoinPool worker threads.
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
     * imdbIds with a single query (instead of one query per id).
     * A title with <strong>no</strong> cache row at all is still fetched synchronously (in
     * parallel with any other such misses, via the proxied {@link #resolve(ImdbId)} through
     * {@link #self}) — there is nothing to show otherwise.
     * A title with an <strong>existing but invalidated/expired</strong> row is different
     * (ADR-0016): its stale cached results are returned immediately (marked
     * {@link ResolvedEntry#stale()}) and a refresh is kicked off in the background instead of
     * blocking this call — this is what lets a page render instantly even right after a bulk
     * invalidation from "Cache Verwalten", instead of the request paying for every re-scrape.
     * Returns the results keyed by imdbId, preserving the iteration order of {@code imdbIds}.
     */
    @LogExecutionTime
    public Map<ImdbId, ResolvedEntry> resolveAll(Collection<ImdbId> imdbIds) {
        final var now = timeService.now();
        final var byImdbId = queryMetaRepository.findByImdbIdIn(imdbIds).stream()
                .collect(Collectors.groupingBy(QueryMeta::getImdbId));

        final var existing = new HashMap<ImdbId, ResolvedEntry>();
        final var misses = new ArrayList<ImdbId>();
        for (ImdbId imdbId : imdbIds) {
            final var latest = byImdbId.getOrDefault(imdbId, List.of()).stream()
                    .max(Comparator.comparing(QueryMeta::getCreationTime));
            if (latest.isEmpty()) {
                misses.add(imdbId);
                continue;
            }
            final var queryMeta = latest.get();
            final var results = toQueryResults(queryMeta);
            if (!queryMeta.isInvalidated() && isFresh(queryMeta, now)) {
                existing.put(imdbId, new ResolvedEntry(results, false));
            } else {
                existing.put(imdbId, new ResolvedEntry(results, true));
                triggerBackgroundRefresh(imdbId);
            }
        }

        final var tx = self.getObject();
        final var fetched = misses.parallelStream()
                .collect(Collectors.toConcurrentMap(imdbId -> imdbId, tx::resolve));

        final var resolved = new LinkedHashMap<ImdbId, ResolvedEntry>();
        for (ImdbId imdbId : imdbIds) {
            resolved.put(imdbId, existing.containsKey(imdbId)
                    ? existing.get(imdbId)
                    : new ResolvedEntry(fetched.get(imdbId), false));
        }
        return resolved;
    }

    /** Starts a background refresh for {@code imdbId} unless one is already under way. */
    private void triggerBackgroundRefresh(ImdbId imdbId) {
        if (refreshInFlightTracker.tryStart(imdbId)) {
            self.getObject().refreshInBackground(imdbId);
        }
    }

    /**
     * Re-scrapes {@code imdbId} on the {@code cacheRefreshExecutor} (ADR-0016), independent of
     * the request that triggered it. Must be called through {@link #self} (see the field
     * Javadoc) for {@code @Async} to actually apply.
     */
    @Async("cacheRefreshExecutor")
    public void refreshInBackground(ImdbId imdbId) {
        try {
            self.getObject().resolve(imdbId, true);
        } catch (RuntimeException e) {
            log.warn("Background refresh for {} failed", imdbId, e);
        } finally {
            refreshInFlightTracker.finish(imdbId);
        }
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
        final var creationTime = timeService.now();
        queryMetaRepository.save(QueryMeta.of(imdbId, creationTime, computeDueForRefreshAt(creationTime), list));
        return queryResults;
    }

    /**
     * The scheduled background job (ADR-0016) treats a row as due once it passes this point —
     * randomised between {@code jitterMinFactor}x and {@code jitterMaxFactor}x
     * {@code afterDays} (default 1.5x-2x, i.e. 42-56 days at the default 28-day TTL) so titles
     * cached around the same time (e.g. a bulk import) don't all become due together.
     */
    private Instant computeDueForRefreshAt(Instant creationTime) {
        final var afterDaysSeconds = TimeUnit.DAYS.toSeconds(properties.invalidate().afterDays());
        final var jitterFactor = ThreadLocalRandom.current()
                .nextDouble(properties.invalidate().jitterMinFactor(), properties.invalidate().jitterMaxFactor());
        return creationTime.plusSeconds((long) (afterDaysSeconds * jitterFactor));
    }
}
