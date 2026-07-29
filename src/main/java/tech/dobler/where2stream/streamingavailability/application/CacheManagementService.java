package tech.dobler.where2stream.streamingavailability.application;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import tech.dobler.where2stream.streamingavailability.application.dto.CacheResultDto;
import tech.dobler.where2stream.streamingavailability.application.dto.InvalidateResultDto;
import tech.dobler.where2stream.streamingavailability.application.dto.ManagePageDto;
import tech.dobler.where2stream.streamingavailability.application.dto.ManageRowDto;
import tech.dobler.where2stream.streamingavailability.application.dto.ScrapeResultDto;
import tech.dobler.where2stream.streamingavailability.application.dto.UncachedCountDto;
import tech.dobler.where2stream.shared.domain.ImdbId;
import tech.dobler.where2stream.watchlist.domain.ImdbEntry;
import tech.dobler.where2stream.watchlist.port.in.WatchlistCatalogPort;
import tech.dobler.where2stream.titlecatalog.port.in.TitleCacheMaintenancePort;
import tech.dobler.where2stream.streamingavailability.application.PreCacheService;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Global (ADMIN) cache-management use cases: view the manage table, invalidate titles,
 * (re-)scrape missing ones, pre-cache all, count uncached.
 * The manage table lists the distinct titles across ALL users' watchlists
 * (the werstreamt.es cache is global); a title counts as "rated" if any user rated it.
 *
 * <p>Deliberately not {@code @Transactional}: {@link PreCacheService} fans out over a
 * {@code parallelStream} relying on per-thread transactions from the proxied
 * {@code StreamInfoService}.
 *
 * <p>ADMIN-only (enforced both by the {@code /api/manage/**}/{@code /api/cache/**} URL rules in
 * {@code SecurityConfig} and {@link PreAuthorize} here as defense in depth, mirroring
 * {@code UserAdminService}).
 */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CacheManagementService {

    private final WatchlistCatalogPort watchlistCatalogPort;
    private final PreCacheService preCacheService;
    private final TitleCacheMaintenancePort titleCacheMaintenancePort;

    private record TitleAgg(String name, boolean rated) {
    }

    public ManagePageDto managePage() {
        final Set<ImdbId> needsScrape = Set.copyOf(preCacheService.findUncachedImdbIds());

        // Distinct titles across all users, merging "rated" (rated by anyone).
        final Map<ImdbId, TitleAgg> byImdbId = new LinkedHashMap<>();
        for (ImdbEntry e : watchlistCatalogPort.findAll()) {
            byImdbId.merge(e.imdbId(), new TitleAgg(e.name(), e.isRated()),
                    (a, b) -> new TitleAgg(a.name(), a.rated() || b.rated()));
        }

        final List<ManageRowDto> rows = byImdbId.entrySet().stream()
                .map(e -> new ManageRowDto(e.getKey(), e.getValue().name(), e.getValue().rated(),
                        needsScrape.contains(e.getKey())))
                .sorted(Comparator.comparing(ManageRowDto::name))
                .toList();
        return new ManagePageDto(rows, needsScrape.size());
    }

    public InvalidateResultDto invalidate(List<ImdbId> imdbIds) {
        final var ids = imdbIds == null ? List.<ImdbId>of() : imdbIds;
        return new InvalidateResultDto(preCacheService.invalidate(ids));
    }

    public ScrapeResultDto scrapeUncached() {
        return new ScrapeResultDto(preCacheService.cacheUncached());
    }

    public CacheResultDto cacheAll() {
        final int cached = preCacheService.cacheAll();
        warmPosterThumbnails();
        return new CacheResultDto(cached);
    }

    /** Delegates to Title Catalog's own maintenance port; this service only supplies which titles. */
    private void warmPosterThumbnails() {
        titleCacheMaintenancePort.warmPosterThumbnails(watchlistCatalogPort.allDistinctImdbIds());
    }

    public UncachedCountDto uncachedCount() {
        return new UncachedCountDto(preCacheService.findUncachedImdbIds().size());
    }
}
