package tech.dobler.werstreamt.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.transaction.annotation.Transactional;
import tech.dobler.werstreamt.domain.ImdbId;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository behaviour for {@link TitlePosterRepository}, run against both H2 and a Testcontainers
 * MariaDB via the concrete subclasses. Proves the BLOB columns round-trip image bytes on the
 * production database (where BLOB handling differs from H2).
 */
@Transactional
public abstract class AbstractTitlePosterRepositoryTests {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private TitlePosterRepository sut;
    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void setUp() {
        sut.deleteAll();
    }

    @Test
    void storesAndReloadsTheImageBlobs() {
        final var poster = TitlePoster.of(ImdbId.of("tt0482571"), "/prestige.jpg", NOW);
        poster.setThumb(new byte[]{1, 2, 3, 4}, "image/jpeg");
        poster.setFull(new byte[]{5, 6, 7, 8, 9}, "image/jpeg");
        sut.save(poster);
        entityManager.flush();
        entityManager.clear();

        final var loaded = sut.findByImdbId(ImdbId.of("tt0482571")).orElseThrow();
        assertThat(loaded.getPosterPath()).isEqualTo("/prestige.jpg");
        assertThat(loaded.getThumb()).containsExactly(1, 2, 3, 4);
        assertThat(loaded.getFull()).containsExactly(5, 6, 7, 8, 9);
        assertThat(loaded.getThumbContentType()).isEqualTo("image/jpeg");
    }

    @Test
    void storesANegativeRowWithoutImages() {
        sut.save(TitlePoster.of(ImdbId.of("tt9999999"), null, NOW));
        entityManager.flush();
        entityManager.clear();

        final var loaded = sut.findByImdbId(ImdbId.of("tt9999999")).orElseThrow();
        assertThat(loaded.getPosterPath()).isNull();
        assertThat(loaded.getThumb()).isNull();
        assertThat(loaded.getFull()).isNull();
    }
}
