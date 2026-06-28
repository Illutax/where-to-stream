package tech.dobler.werstreamt.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.dobler.werstreamt.domain.ImdbEntry;
import tech.dobler.werstreamt.persistence.QueryMetaRepository;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pre-resolves stream availability for every known IMDb entry so later page views hit the
 * cache. Lives in the service layer so both {@code PreCacheController} and
 * {@code ChangeListController} can trigger it without a controller depending on another
 * controller.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreCacheService {
    private final StreamInfoService streamInfoService;
    private final ImdbCatalog imdbCatalog;
    private final QueryMetaRepository queryMetaRepository;

    /**
     * Resolves every entry (populating the cache as a side effect) and returns how many
     * entries were processed.
     */
    public int cacheAll() {
        final var all = imdbCatalog.findAll();
        final var counter = new AtomicInteger(0);
        all.parallelStream()
                .forEach(e -> {
                    streamInfoService.resolve(e.imdbId());
                    if (counter.incrementAndGet() % 10 == 0) {
                        log.info("got {} imdb entries", counter.get());
                    }
                });
        return counter.get();
    }

    /**
     * Resolves only the entries that currently have no valid cached result — i.e. never-cached
     * titles and ones that were invalidated (see {@link #invalidate}). Returns how many were
     * (re)scraped.
     */
    public int cacheUncached() {
        final var uncached = findUncached();
        uncached.parallelStream().forEach(e -> streamInfoService.resolve(e.imdbId()));
        return uncached.size();
    }

    /**
     * Marks the cached results of the given titles as invalidated so they are refetched on the
     * next resolve / {@link #cacheUncached()} run. Returns the number of cache rows affected.
     */
    @Transactional
    public int invalidate(Collection<String> imdbIds) {
        if (imdbIds.isEmpty()) {
            return 0;
        }
        final int affected = queryMetaRepository.invalidateByImdbIds(imdbIds);
        log.info("Invalidated {} cache rows for {} titles", affected, imdbIds.size());
        return affected;
    }

    /**
     * Returns the entries that currently have no valid cached query result (never cached or
     * invalidated).
     */
    public List<ImdbEntry> findUncached() {
        return imdbCatalog.findAll().parallelStream()
                .filter(e -> queryMetaRepository
                        .findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc(e.imdbId())
                        .isEmpty())
                .toList();
    }
}
