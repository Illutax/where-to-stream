package tech.dobler.where2stream.streamingavailability.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import tech.dobler.where2stream.streamingavailability.application.dto.RefreshResultDto;
import tech.dobler.where2stream.streamingavailability.domain.ScrapingException;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.watchlist.port.in.WatchlistCatalogPort;
import tech.dobler.where2stream.streamingavailability.application.StreamInfoService;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Force-refreshes cached stream availability across all users' watchlists (global, ADMIN),
 * for all titles or only the seen ones.
 *
 * <p>Deliberately not {@code @Transactional}: the refresh fans out over a {@code parallelStream}
 * and each worker calls the proxied {@link StreamInfoService#resolve(String, boolean)},
 * which opens its own transaction per thread (see the NOTE in {@code StreamInfoService}).
 *
 * <p>ADMIN-only (enforced both by the {@code POST /api/refresh} URL rule in {@code SecurityConfig}
 * and {@link PreAuthorize} here as defense in depth, mirroring {@code UserAdminService}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class RefreshService {

    private final WatchlistCatalogPort watchlistCatalogPort;
    private final StreamInfoService streamInfoService;

    public RefreshResultDto refreshSeen() {
        return refresh(watchlistCatalogPort.allDistinctRatedImdbIds());
    }

    public RefreshResultDto refreshAll() {
        return refresh(watchlistCatalogPort.allDistinctImdbIds());
    }

    private RefreshResultDto refresh(List<ImdbId> imdbIds) {
        log.info("Refreshing {} titles", imdbIds.size());
        // One failing title must not abort the whole (non-resumable) run: skip it, keep its old
        // cache row, and report the failure count instead of answering 502 halfway through.
        final var failed = new AtomicInteger();
        final var refreshed = imdbIds.parallelStream()
                .filter(imdbId -> refreshOne(imdbId, failed))
                .toList();
        if (failed.get() > 0) {
            log.warn("Refresh finished with {} of {} titles failed", failed.get(), imdbIds.size());
        }
        return new RefreshResultDto(refreshed.size(), failed.get());
    }

    private boolean refreshOne(ImdbId imdbId, AtomicInteger failed) {
        try {
            streamInfoService.resolve(imdbId, true);
            return true;
        } catch (ScrapingException e) {
            log.warn("Refresh of {} failed; skipping it", imdbId, e);
            failed.incrementAndGet();
            return false;
        }
    }
}
