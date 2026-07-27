package tech.dobler.werstreamt.persistence;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tech.dobler.werstreamt.domain.ImdbId;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TitleMetaRepository extends CrudRepository<TitleMeta, UUID> {
    Optional<TitleMeta> findByImdbId(ImdbId imdbId);
}
