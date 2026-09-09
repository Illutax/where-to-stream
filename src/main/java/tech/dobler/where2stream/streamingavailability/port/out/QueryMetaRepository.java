package tech.dobler.where2stream.streamingavailability.port.out;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.streamingavailability.domain.QueryMeta;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QueryMetaRepository extends CrudRepository<QueryMeta, UUID> {
    Optional<QueryMeta> findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc(ImdbId imdbId);

    // Like the above, but without the invalidated filter — used where an invalidated row's
    // creationTime is still of interest (e.g. "last scraped at" on the manage table).
    // May return several rows per imdbId; the caller picks the latest.
    List<QueryMeta> findByImdbIdIn(Collection<ImdbId> imdbIds);

    // Marks the still-valid cache entries of the given titles as invalidated, so the next
    // resolve()/scrape refetches them. Returns the number of rows affected.
    @Modifying
    @Query("update QueryMeta q set q.invalidated = true where q.imdbId in :imdbIds and q.invalidated = false")
    int invalidateByImdbIds(@Param("imdbIds") Collection<ImdbId> imdbIds);

    /** Distinct titles that have any availability cache row at all, fresh or not. */
    @Query("select count(distinct q.imdbId) from QueryMeta q")
    long countCachedTitles();

    /**
     * Distinct titles that still have a usable row — the same condition
     * {@code findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc} and
     * {@code BackgroundCacheRefreshService.isDue} apply, expressed once as an aggregate.
     *
     * <p>A null {@code dueForRefreshAt} counts as fresh, matching that service: those are rows
     * written before the column existed, and they go stale only through invalidation.
     *
     * <p>Stale is then "cached minus fresh" rather than its own query. A title can hold several
     * rows, so counting stale ones directly would report a title as both — one fresh row is what
     * makes a title served from cache, no matter how many expired ones sit beside it.
     */
    @Query("select count(distinct q.imdbId) from QueryMeta q where q.invalidated = false "
            + "and (q.dueForRefreshAt is null or q.dueForRefreshAt >= :now)")
    long countFreshTitles(@Param("now") Instant now);
}
