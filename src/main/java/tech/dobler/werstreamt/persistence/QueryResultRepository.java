package tech.dobler.werstreamt.persistence;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QueryResultRepository extends CrudRepository<QueryResultDB, UUID> {
    List<QueryResultDB> findByImdbId(String imdbId);
}
