package tech.dobler.werstreamt.persistence;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;
import tech.dobler.werstreamt.domain.ImdbId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WatchlistEntryRepository extends ListCrudRepository<WatchlistEntry, UUID> {

    List<WatchlistEntry> findByUserId(UUID userId);

    List<WatchlistEntry> findByUserIdAndRatedTrue(UUID userId);

    Optional<WatchlistEntry> findByUserIdAndImdbId(UUID userId, ImdbId imdbId);

    long countByUserId(UUID userId);

    void deleteByUserId(UUID userId);

    // Selected as the raw column (native) rather than JPQL `select w.imdbId`: a single-path JPQL
    // projection onto the ImdbId value type makes Spring Data attempt a DTO constructor expression
    // (`new ImdbId(...)`), which fails. The default methods below wrap the strings back into ImdbId.
    @Query(value = "select distinct imdb_id from watchlist_entry", nativeQuery = true)
    List<String> distinctImdbIdValues();

    @Query(value = "select distinct imdb_id from watchlist_entry where is_rated = true", nativeQuery = true)
    List<String> distinctRatedImdbIdValues();

    /** All distinct titles across every user's watchlist — for the global (ADMIN) cache maintenance. */
    default List<ImdbId> findDistinctImdbIds() {
        return distinctImdbIdValues().stream().map(ImdbId::of).toList();
    }

    default List<ImdbId> findDistinctImdbIdsRated() {
        return distinctRatedImdbIdValues().stream().map(ImdbId::of).toList();
    }

    @Query("select max(w.createdAt) from WatchlistEntry w where w.userId = :userId")
    Optional<Instant> findLastImportedAt(UUID userId);
}
