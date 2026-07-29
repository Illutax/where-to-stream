package tech.dobler.where2stream.persistence;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tech.dobler.where2stream.domain.ImdbId;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TitleMetaRepository extends CrudRepository<TitleMeta, UUID> {
    Optional<TitleMeta> findByImdbId(ImdbId imdbId);
}
