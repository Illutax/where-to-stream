package tech.dobler.where2stream.watchlist.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tech.dobler.where2stream.watchlist.domain.ImdbEntry;
import tech.dobler.where2stream.shared.domain.ImdbId;
import tech.dobler.where2stream.watchlist.domain.WatchlistEntry;
import tech.dobler.where2stream.watchlist.port.out.WatchlistEntryRepository;
import tech.dobler.where2stream.watchlist.port.in.WatchlistCatalogPort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read access to a user's watchlist, backed by the database (replaces the former global,
 * in-memory {@code ImdbCatalog}).
 * Most methods are scoped to a {@code userId};
 * the werstreamt.es availability cache stays global and is not touched here.
 */
@Service
@RequiredArgsConstructor
public class WatchlistCatalog implements WatchlistCatalogPort {

    private final WatchlistEntryRepository repository;

    @Override
    public List<ImdbEntry> findAll(UUID userId) {
        return repository.findByUserId(userId).stream().map(WatchlistCatalog::toEntry).toList();
    }

    @Override
    public List<ImdbEntry> findAll() {
        return repository.findAll().stream().map(WatchlistCatalog::toEntry).toList();
    }

    public List<ImdbEntry> findAllSeen(UUID userId) {
        return repository.findByUserIdAndRatedTrue(userId).stream().map(WatchlistCatalog::toEntry).toList();
    }

    @Override
    public Optional<ImdbEntry> findByImdb(UUID userId, ImdbId imdbId) {
        return repository.findByUserIdAndImdbId(userId, imdbId).map(WatchlistCatalog::toEntry);
    }

    @Override
    public boolean isOnWatchlist(UUID userId, ImdbId imdbId) {
        return repository.existsByUserIdAndImdbId(userId, imdbId);
    }

    @Override
    public List<ImdbId> allDistinctImdbIds() {
        return repository.findDistinctImdbIds();
    }

    @Override
    public List<ImdbId> allDistinctRatedImdbIds() {
        return repository.findDistinctImdbIdsRated();
    }

    public long count(UUID userId) {
        return repository.countByUserId(userId);
    }

    private static ImdbEntry toEntry(WatchlistEntry w) {
        return new ImdbEntry(w.getName(), w.urlAsUri(), w.getAdded(), w.isRated(), w.getYear(), w.getImdbId());
    }
}
