package tech.dobler.where2stream.persistence;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tech.dobler.where2stream.shared.domain.ImdbId;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TitlePosterRepository extends CrudRepository<TitlePoster, UUID> {
    Optional<TitlePoster> findByImdbId(ImdbId imdbId);
}
