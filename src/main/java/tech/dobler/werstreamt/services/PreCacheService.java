package tech.dobler.werstreamt.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.dobler.werstreamt.persistence.QueryMetaRepository;
import tech.dobler.werstreamt.persistence.WatchlistEntryRepository;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pre-resolves stream availability so later page views hit the cache. Operates on the union of
 * every user's watchlist titles (distinct imdbIds), since the werstreamt.es cache is global — the
 * global (ADMIN) cache maintenance and the per-import targeted pre-cache both use this service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreCacheService {
    private final StreamInfoService streamInfoService;
    private final WatchlistEntryRepository watchlistEntryRepository;
    private final QueryMetaRepository queryMetaRepository;

    /** Resolves every known title (across all users) and returns how many were processed. */
    public int cacheAll() {
        return cache(watchlistEntryRepository.findDistinctImdbIds());
    }

    /** Resolves the given titles (populating the cache) and returns how many were processed. */
    public int cache(Collection<String> imdbIds) {
        final var counter = new AtomicInteger(0);
        imdbIds.parallelStream()
                .forEach(imdbId -> {
                    streamInfoService.resolve(imdbId);
                    if (counter.incrementAndGet() % 10 == 0) {
                        log.info("resolved {} titles", counter.get());
                    }
                });
        return counter.get();
    }

    /** Resolves only the titles with no valid cached result (never cached or invalidated). */
    public int cacheUncached() {
        final var uncached = findUncachedImdbIds();
        uncached.parallelStream().forEach(streamInfoService::resolve);
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

    /** The distinct titles (across all users) that currently have no valid cached query result. */
    public List<String> findUncachedImdbIds() {
        return watchlistEntryRepository.findDistinctImdbIds().parallelStream()
                .filter(imdbId -> queryMetaRepository
                        .findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc(imdbId)
                        .isEmpty())
                .toList();
    }
}
