package tech.dobler.where2stream.titlecatalog.port.out;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.titlecatalog.domain.TitlePoster;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TitlePosterRepository extends CrudRepository<TitlePoster, UUID> {
    Optional<TitlePoster> findByImdbId(ImdbId imdbId);
}
