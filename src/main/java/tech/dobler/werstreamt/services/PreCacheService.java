package tech.dobler.werstreamt.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.dobler.werstreamt.domain.ImdbEntry;
import tech.dobler.werstreamt.persistence.QueryMetaRepository;

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
    private final ImdbEntryRepository imdbEntryRepository;
    private final QueryMetaRepository queryMetaRepository;

    /**
     * Resolves every entry (populating the cache as a side effect) and returns how many
     * entries were processed.
     */
    public int cacheAll() {
        final var all = imdbEntryRepository.findAll();
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
     * Returns the entries that currently have no valid cached query result and logs each
     * one as a warning.
     */
    public List<ImdbEntry> findUncached() {
        final var uncached = imdbEntryRepository.findAll().parallelStream()
                .filter(e -> queryMetaRepository
                        .findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc(e.imdbId())
                        .isEmpty())
                .toList();
        uncached.forEach(e -> log.warn("No cached query result for {}", e));
        return uncached;
    }
}
