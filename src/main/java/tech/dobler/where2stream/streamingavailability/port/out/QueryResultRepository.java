package tech.dobler.where2stream.streamingavailability.port.out;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.streamingavailability.domain.QueryResultDB;

import java.util.List;
import java.util.UUID;

@Repository
public interface QueryResultRepository extends CrudRepository<QueryResultDB, UUID> {
    List<QueryResultDB> findByImdbId(ImdbId imdbId);
}
