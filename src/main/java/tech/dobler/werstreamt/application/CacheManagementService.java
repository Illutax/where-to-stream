package tech.dobler.werstreamt.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tech.dobler.werstreamt.application.dto.CacheResultDto;
import tech.dobler.werstreamt.application.dto.InvalidateResultDto;
import tech.dobler.werstreamt.application.dto.ManagePageDto;
import tech.dobler.werstreamt.application.dto.ManageRowDto;
import tech.dobler.werstreamt.application.dto.ScrapeResultDto;
import tech.dobler.werstreamt.application.dto.UncachedCountDto;
import tech.dobler.werstreamt.domain.ImdbEntry;
import tech.dobler.werstreamt.services.ImdbCatalog;
import tech.dobler.werstreamt.services.PreCacheService;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cache-management use cases (view the manage table, invalidate titles, (re-)scrape missing
 * ones, pre-cache all, count uncached). Shapes the manage table and delegates the actual work
 * to {@link PreCacheService}, so the Thymeleaf {@code /manage} UI and the {@code /api/manage}
 * endpoints behave identically.
 *
 * <p>Deliberately not {@code @Transactional}: {@link PreCacheService#cacheAll()} /
 * {@code cacheUncached()} fan out over a {@code parallelStream} and rely on each worker thread
 * opening its own transaction via the proxied {@code StreamInfoService} (see the NOTE in
 * {@code StreamInfoService}); wrapping them here would not span those threads anyway.
 */
@Service
@RequiredArgsConstructor
public class CacheManagementService {

    private final ImdbCatalog imdbCatalog;
    private final PreCacheService preCacheService;

    public ManagePageDto managePage() {
        final Set<String> needsScrape = preCacheService.findUncached().stream()
                .map(ImdbEntry::imdbId)
                .collect(Collectors.toSet());
        final List<ManageRowDto> rows = imdbCatalog.findAll().stream()
                .sorted(Comparator.comparing(ImdbEntry::name))
                .map(e -> new ManageRowDto(e.imdbId(), e.name(), e.isRated(), needsScrape.contains(e.imdbId())))
                .toList();
        return new ManagePageDto(rows, needsScrape.size());
    }

    public InvalidateResultDto invalidate(List<String> imdbIds) {
        final var ids = imdbIds == null ? List.<String>of() : imdbIds;
        return new InvalidateResultDto(preCacheService.invalidate(ids));
    }

    public ScrapeResultDto scrapeUncached() {
        return new ScrapeResultDto(preCacheService.cacheUncached());
    }

    public CacheResultDto cacheAll() {
        return new CacheResultDto(preCacheService.cacheAll());
    }

    public UncachedCountDto uncachedCount() {
        return new UncachedCountDto(preCacheService.findUncached().size());
    }
}
