package tech.dobler.where2stream.streamingavailability.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.shared.platform.concurrency.RefreshInFlightTracker;
import tech.dobler.where2stream.shared.platform.time.TimeService;
import tech.dobler.where2stream.streamingavailability.domain.QueryMeta;
import tech.dobler.where2stream.streamingavailability.port.out.QueryMetaRepository;
import tech.dobler.where2stream.watchlist.port.in.WatchlistCatalogPort;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Proactively refreshes titles nobody is actively viewing (ADR-0016): the demand-driven path in
 * {@link StreamInfoService#resolveAll} only refreshes a stale title when a page request actually
 * asks for it, so a title on nobody's currently-opened page would otherwise stay stale
 * indefinitely. {@link CacheRefreshScheduler} (a scheduled job, coarse cadence) is the sole
 * caller — this class holds the query/dedup logic, kept separate so it stays unit-testable
 * without touching Spring's scheduling machinery.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackgroundCacheRefreshService {

    private final WatchlistCatalogPort watchlistCatalogPort;
    private final QueryMetaRepository queryMetaRepository;
    private final RefreshInFlightTracker tracker;
    private final StreamInfoService streamInfoService;
    private final TimeService timeService;

    /** @return how many titles were actually queued (excludes ones already in flight). */
    public int refreshDueEntries() {
        final var imdbIds = watchlistCatalogPort.allDistinctImdbIds();
        if (imdbIds.isEmpty()) {
            return 0;
        }

        final var now = timeService.now();
        // Same batch-load + reduce-to-latest-per-imdbId pattern as StreamInfoService.resolveAll /
        // CacheManagementService.managePage — a title can have several historical rows, only the
        // newest one determines whether it's due.
        final var latestByImdbId = queryMetaRepository.findByImdbIdIn(imdbIds).stream()
                .collect(Collectors.groupingBy(QueryMeta::getImdbId,
                        Collectors.collectingAndThen(
                                Collectors.maxBy(Comparator.comparing(QueryMeta::getCreationTime)),
                                Optional::orElseThrow)));

        final var due = latestByImdbId.entrySet().stream()
                .filter(e -> isDue(e.getValue(), now))
                .map(Map.Entry::getKey)
                .toList();
        if (due.isEmpty()) {
            log.debug("Background cache refresh: nothing due");
            return 0;
        }

        final var started = due.stream().filter(tracker::tryStart).toList();
        started.forEach(streamInfoService::refreshInBackground);
        log.info("Background cache refresh: {} titles queued ({} already in flight)",
                started.size(), due.size() - started.size());
        return started.size();
    }

    /**
     * A row with no {@code dueForRefreshAt} (written before that column existed) is only due via
     * the {@code invalidated} branch — it becomes eligible on the jitter schedule once it is next
     * (re-)scraped, same as documented on {@link QueryMeta#getDueForRefreshAt()}.
     */
    private static boolean isDue(QueryMeta latest, Instant now) {
        if (latest.isInvalidated()) {
            return true;
        }
        final var dueForRefreshAt = latest.getDueForRefreshAt();
        return dueForRefreshAt != null && dueForRefreshAt.isBefore(now);
    }
}
