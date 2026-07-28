package tech.dobler.werstreamt.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import tech.dobler.werstreamt.domain.AgeRating.RatingSystem;
import tech.dobler.werstreamt.domain.ImdbId;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Round-trips the IMDb metadata cache (poster path + rating enum/label) on the embedded H2 database. */
@DataJpaTest
class TitleMetaRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private TitleMetaRepository sut;
    @Autowired
    private TestEntityManager entityManager;

    @Test
    void storesAndReloadsPosterPathRatingAndGermanTitle() {
        sut.save(TitleMeta.of(ImdbId.of("tt0110912"), "/pulp.jpg", RatingSystem.FSK, "16", "Pulp Fiction", NOW));
        entityManager.flush();
        entityManager.clear();

        final var loaded = sut.findByImdbId(ImdbId.of("tt0110912")).orElseThrow();
        assertThat(loaded)
                .extracting(TitleMeta::getPosterPath, TitleMeta::getRatingSystem, TitleMeta::getRatingLabel,
                        TitleMeta::getGermanTitle)
                .containsExactly("/pulp.jpg", RatingSystem.FSK, "16", "Pulp Fiction");
    }

    @Test
    void storesANegativeRowWithAllNullData() {
        sut.save(TitleMeta.of(ImdbId.of("tt9999999"), null, null, null, null, NOW));
        entityManager.flush();
        entityManager.clear();

        final var loaded = sut.findByImdbId(ImdbId.of("tt9999999")).orElseThrow();
        assertThat(loaded)
                .extracting(TitleMeta::getPosterPath, TitleMeta::getRatingSystem, TitleMeta::getRatingLabel,
                        TitleMeta::getGermanTitle)
                .containsOnlyNulls();
    }
}
