package tech.dobler.werstreamt.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tech.dobler.werstreamt.application.dto.CacheResultDto;
import tech.dobler.werstreamt.application.dto.InvalidateResultDto;
import tech.dobler.werstreamt.application.dto.ManagePageDto;
import tech.dobler.werstreamt.application.dto.ManageRowDto;
import tech.dobler.werstreamt.application.dto.ScrapeResultDto;
import tech.dobler.werstreamt.application.dto.UncachedCountDto;
import tech.dobler.werstreamt.domain.ImdbId;
import tech.dobler.werstreamt.persistence.WatchlistEntry;
import tech.dobler.werstreamt.persistence.WatchlistEntryRepository;
import tech.dobler.werstreamt.services.PreCacheService;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Global (ADMIN) cache-management use cases: view the manage table, invalidate titles, (re-)scrape
 * missing ones, pre-cache all, count uncached. The manage table lists the distinct titles across
 * ALL users' watchlists (the werstreamt.es cache is global); a title counts as "rated" if any user
 * rated it.
 *
 * <p>Deliberately not {@code @Transactional}: {@link PreCacheService} fans out over a
 * {@code parallelStream} relying on per-thread transactions from the proxied
 * {@code StreamInfoService}.
 */
@Service
@RequiredArgsConstructor
public class CacheManagementService {

    private final WatchlistEntryRepository watchlistEntryRepository;
    private final PreCacheService preCacheService;

    private record TitleAgg(String name, boolean rated) {
    }

    public ManagePageDto managePage() {
        final Set<ImdbId> needsScrape = Set.copyOf(preCacheService.findUncachedImdbIds());

        // Distinct titles across all users, merging "rated" (rated by anyone).
        final Map<ImdbId, TitleAgg> byImdbId = new LinkedHashMap<>();
        for (WatchlistEntry w : watchlistEntryRepository.findAll()) {
            byImdbId.merge(w.getImdbId(), new TitleAgg(w.getName(), w.isRated()),
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
        return new CacheResultDto(preCacheService.cacheAll());
    }

    public UncachedCountDto uncachedCount() {
        return new UncachedCountDto(preCacheService.findUncachedImdbIds().size());
    }
}
