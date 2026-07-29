package tech.dobler.where2stream.titlecatalog.port.out;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.transaction.annotation.Transactional;
import tech.dobler.where2stream.shared.domain.ImdbId;
import tech.dobler.where2stream.titlecatalog.domain.TitlePoster;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository behaviour for {@link TitlePosterRepository},
 * run against both H2 and a Testcontainers MariaDB via the concrete subclasses.
 * Proves the BLOB columns round-trip image bytes on the production database
 * (where BLOB handling differs from H2).
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
        assertThat(loaded).extracting(TitlePoster::getPosterPath, TitlePoster::getThumbContentType)
                .containsExactly("/prestige.jpg", "image/jpeg");
        assertThat(loaded.getThumb()).containsExactly(1, 2, 3, 4);
        assertThat(loaded.getFull()).containsExactly(5, 6, 7, 8, 9);
    }

    @Test
    void storesAHiResFullImageLargerThanMariaDbsBlobLimit() {
        // A hi-res poster exceeds MariaDB's 64 KB plain-BLOB cap; full_bytes must be a larger type.
        final byte[] large = new byte[200_000];
        java.util.Arrays.fill(large, (byte) 7);
        final var poster = TitlePoster.of(ImdbId.of("tt1375666"), "/inception.jpg", NOW);
        poster.setFull(large, "image/jpeg");
        sut.save(poster);
        entityManager.flush();
        entityManager.clear();

        final var loaded = sut.findByImdbId(ImdbId.of("tt1375666")).orElseThrow();
        assertThat(loaded.getFull()).hasSize(200_000);
    }

    @Test
    void storesANegativeRowWithoutImages() {
        sut.save(TitlePoster.of(ImdbId.of("tt9999999"), null, NOW));
        entityManager.flush();
        entityManager.clear();

        final var loaded = sut.findByImdbId(ImdbId.of("tt9999999")).orElseThrow();
        assertThat(loaded).extracting(TitlePoster::getPosterPath, TitlePoster::getThumb, TitlePoster::getFull)
                .containsOnlyNulls();
    }
}
