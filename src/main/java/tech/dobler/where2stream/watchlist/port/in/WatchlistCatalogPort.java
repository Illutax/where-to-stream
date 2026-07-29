package tech.dobler.where2stream.watchlist.port.in;

import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.watchlist.domain.ImdbEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read access to watchlist facts, published so other bounded contexts can resolve
 * availability/search/cache-maintenance concerns against a user's (or every user's) watchlist
 * without depending on {@code WatchlistEntryRepository}/{@code WatchlistCatalog} directly.
 */
public interface WatchlistCatalogPort {

    List<ImdbEntry> findAll(UUID userId);

    /** Every watchlist entry across every user (duplicates possible — one imdbId per user's entry). */
    List<ImdbEntry> findAll();

    Optional<ImdbEntry> findByImdb(UUID userId, ImdbId imdbId);

    boolean isOnWatchlist(UUID userId, ImdbId imdbId);

    List<ImdbId> allDistinctImdbIds();

    List<ImdbId> allDistinctRatedImdbIds();
}
