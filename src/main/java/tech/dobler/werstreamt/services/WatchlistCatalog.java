package tech.dobler.werstreamt.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tech.dobler.werstreamt.domain.ImdbEntry;
import tech.dobler.werstreamt.domain.ImdbId;
import tech.dobler.werstreamt.persistence.WatchlistEntry;
import tech.dobler.werstreamt.persistence.WatchlistEntryRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read access to a single user's watchlist, backed by the database (replaces the former global,
 * in-memory {@code ImdbCatalog}). Every method is scoped to a {@code userId}; the werstreamt.es
 * availability cache stays global and is not touched here.
 */
@Service
@RequiredArgsConstructor
public class WatchlistCatalog {

    private final WatchlistEntryRepository repository;

    public List<ImdbEntry> findAll(UUID userId) {
        return repository.findByUserId(userId).stream().map(WatchlistCatalog::toEntry).toList();
    }

    public List<ImdbEntry> findAllSeen(UUID userId) {
        return repository.findByUserIdAndRatedTrue(userId).stream().map(WatchlistCatalog::toEntry).toList();
    }

    public Optional<ImdbEntry> findByImdb(UUID userId, ImdbId imdbId) {
        return repository.findByUserIdAndImdbId(userId, imdbId).map(WatchlistCatalog::toEntry);
    }

    public long count(UUID userId) {
        return repository.countByUserId(userId);
    }

    private static ImdbEntry toEntry(WatchlistEntry w) {
        return new ImdbEntry(w.getName(), w.urlAsUri(), w.getAdded(), w.isRated(), w.getYear(), w.getImdbId());
    }
}
