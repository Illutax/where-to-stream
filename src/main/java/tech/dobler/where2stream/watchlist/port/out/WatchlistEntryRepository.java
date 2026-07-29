package tech.dobler.where2stream.watchlist.port.out;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.watchlist.domain.WatchlistEntry;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WatchlistEntryRepository extends ListCrudRepository<WatchlistEntry, UUID> {

    List<WatchlistEntry> findByUserId(UUID userId);

    List<WatchlistEntry> findByUserIdAndRatedTrue(UUID userId);

    Optional<WatchlistEntry> findByUserIdAndImdbId(UUID userId, ImdbId imdbId);

    boolean existsByUserIdAndImdbId(UUID userId, ImdbId imdbId);

    long countByUserId(UUID userId);

    void deleteByUserId(UUID userId);

    long deleteByUserIdAndRatedTrue(UUID userId);

    /** All distinct titles across every user's watchlist — for the global (ADMIN) cache maintenance. */
    @Query("select distinct w.imdbId from WatchlistEntry w")
    List<ImdbId> findDistinctImdbIds();

    @Query("select distinct w.imdbId from WatchlistEntry w where w.rated = true")
    List<ImdbId> findDistinctImdbIdsRated();

    @Query("select max(w.createdAt) from WatchlistEntry w where w.userId = :userId")
    Optional<Instant> findLastImportedAt(UUID userId);
}
