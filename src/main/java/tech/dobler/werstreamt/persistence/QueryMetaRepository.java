package tech.dobler.werstreamt.persistence;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QueryMetaRepository extends CrudRepository<QueryMeta, UUID> {
    Optional<QueryMeta> findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc(String imdbId);

    // Batch variant used to resolve many imdbIds with a single query (avoids N+1 on the
    // index page). May return several rows per imdbId; the caller picks the latest.
    List<QueryMeta> findByImdbIdInAndInvalidatedIsFalse(Collection<String> imdbIds);

    // Marks the still-valid cache entries of the given titles as invalidated, so the next
    // resolve()/scrape refetches them. Returns the number of rows affected.
    @Modifying
    @Query("update QueryMeta q set q.invalidated = true where q.imdbId in :imdbIds and q.invalidated = false")
    int invalidateByImdbIds(@Param("imdbIds") Collection<String> imdbIds);
}
