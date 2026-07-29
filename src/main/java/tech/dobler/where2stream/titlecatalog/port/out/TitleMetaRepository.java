package tech.dobler.where2stream.titlecatalog.port.out;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tech.dobler.where2stream.shared.domain.ImdbId;
import tech.dobler.where2stream.titlecatalog.domain.TitleMeta;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TitleMetaRepository extends CrudRepository<TitleMeta, UUID> {
    Optional<TitleMeta> findByImdbId(ImdbId imdbId);
}
