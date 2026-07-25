package tech.dobler.werstreamt.persistence;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WatchlistEntryRepository extends ListCrudRepository<WatchlistEntry, UUID> {

    List<WatchlistEntry> findByUserId(UUID userId);

    List<WatchlistEntry> findByUserIdAndRatedTrue(UUID userId);

    Optional<WatchlistEntry> findByUserIdAndImdbId(UUID userId, String imdbId);

    long countByUserId(UUID userId);

    void deleteByUserId(UUID userId);

    /** All distinct titles across every user's watchlist — for the global (ADMIN) cache maintenance. */
    @Query("select distinct w.imdbId from WatchlistEntry w")
    List<String> findDistinctImdbIds();

    @Query("select distinct w.imdbId from WatchlistEntry w where w.rated = true")
    List<String> findDistinctImdbIdsRated();

    @Query("select max(w.createdAt) from WatchlistEntry w where w.userId = :userId")
    Optional<Instant> findLastImportedAt(UUID userId);
}
