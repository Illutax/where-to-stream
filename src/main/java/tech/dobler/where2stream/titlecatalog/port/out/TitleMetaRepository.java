package tech.dobler.where2stream.titlecatalog.port.out;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.titlecatalog.domain.TitleMeta;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TitleMetaRepository extends CrudRepository<TitleMeta, UUID> {
    Optional<TitleMeta> findByImdbId(ImdbId imdbId);

    /**
     * Rows that record "IMDb had nothing for this title" — every data column null (see
     * {@link tech.dobler.where2stream.titlecatalog.domain.TitleMeta}). They are cache entries like
     * any other, so a plain {@code count()} reports a cache full of absences as a full cache.
     */
    @Query("select count(t) from TitleMeta t where t.posterPath is null and t.ratingSystem is null "
            + "and t.ratingLabel is null and t.germanTitle is null")
    long countWithoutData();
}
