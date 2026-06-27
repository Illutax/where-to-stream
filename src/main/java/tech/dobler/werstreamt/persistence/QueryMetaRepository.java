package tech.dobler.werstreamt.persistence;

import org.springframework.data.repository.CrudRepository;
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
}
