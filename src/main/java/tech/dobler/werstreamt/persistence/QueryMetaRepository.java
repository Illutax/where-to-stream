package tech.dobler.werstreamt.persistence;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface QueryMetaRepository extends CrudRepository<QueryMeta, UUID> {
    Optional<QueryMeta> findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc(String imdbId);
}
