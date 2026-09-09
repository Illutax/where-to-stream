package tech.dobler.where2stream.streamingavailability.port.out;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.transaction.annotation.Transactional;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.streamingavailability.domain.QueryMeta;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

/**
 * Repository behaviour for {@link QueryMetaRepository},
 * run against both H2 and a Testcontainers MariaDB via the concrete subclasses.
 */
public abstract class AbstractQueryMetaRepositoryTests {
    @Autowired
    private QueryMetaRepository sut;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void setup()
    {
        sut.deleteAll();
    }

    /**
     * The freshness aggregate behind the metrics dashboard (TODO-71).
     *
     * <p>Three conditions decide it and all three are exercised here, because the count is only
     * meaningful if it agrees with what the resolver actually serves: an invalidated row is not
     * usable, a row past {@code dueForRefreshAt} is not usable, and a row with no
     * {@code dueForRefreshAt} at all — written before that column existed — is, which is the one
     * a plain "expired?" reading would get backwards.
     *
     * <p>tt1 also holds a stale row beside its fresh one, so the count has to be over distinct
     * titles rather than rows: one usable row is what makes a title served from cache, however
     * many expired ones sit next to it.
     */
    @Test
    @Transactional
    void countsTitlesWithAUsableCacheRowRatherThanRows() {
        final var now = Instant.parse("2026-01-01T00:00:00Z");
        sut.save(new QueryMeta(null, ImdbId.of("tt1"), now, now.plusSeconds(3600), false, List.of()));
        sut.save(new QueryMeta(null, ImdbId.of("tt1"), now, now.minusSeconds(1), false, List.of()));
        sut.save(new QueryMeta(null, ImdbId.of("tt2"), now, now.minusSeconds(1), false, List.of()));
        sut.save(new QueryMeta(null, ImdbId.of("tt3"), now, now.plusSeconds(3600), true, List.of()));
        sut.save(new QueryMeta(null, ImdbId.of("tt4"), now, null, false, List.of()));
        entityManager.flush();
        entityManager.clear();

        // tt1 (one fresh row) and tt4 (legacy row, never due) are usable; tt2 expired, tt3 invalidated.
        assertThat(new long[]{sut.countCachedTitles(), sut.countFreshTitles(now)}).containsExactly(4L, 2L);
    }

    @Test
    @Transactional
    void saveAndLoadOne() {
        // Arrange
//        final var timestamp = Instant.parse("2024-06-17T10:00:00Z");
        final var imdbId = ImdbId.of("tt0123755");
        final var timestamp = Instant.now();
        final var entry = new QueryMeta(null, imdbId, timestamp, null, false, List.of());

        // Act
        final var saveResult = sut.save(entry);
        entityManager.flush();
        entityManager.clear();
        final var loadFromDb = sut.findById(saveResult.getId());

        // Assert
        assertThat(loadFromDb).contains(entry);
    }

    @Test
    @Transactional
    void findByImdbId() {
        // Arrange
        final var imdbId = ImdbId.of("tt0123755");
        final var timestamp = Instant.now();
        final var entry = new QueryMeta(null, imdbId, timestamp, null, false, List.of());

        // Act
        sut.save(entry);
        entityManager.flush();
        entityManager.clear();
        final var loadFromDb = sut.findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc(imdbId);

        // Assert
        assertThat(loadFromDb).contains(entry);
    }

    @Test
    @Transactional
    void findByImdbId_saveThree_returnNewest() {
        // Arrange
        final var imdbId = ImdbId.of("tt0123755");
        final var timestamp = Instant.parse("2024-06-15T10:15:30Z");
        final var entry = new QueryMeta(null, imdbId, timestamp, null, false, List.of());
        final var entry2 = new QueryMeta(null, imdbId, timestamp.plusSeconds(15), null, false, List.of());
        final var entry3 = new QueryMeta(null, imdbId, timestamp.plusSeconds(20), null, true, List.of());
        final var entry4 = new QueryMeta(null, imdbId, timestamp.minusSeconds(15), null, false, List.of());

        // Act
        sut.saveAll(List.of(entry, entry2, entry3, entry4));
        entityManager.flush();
        entityManager.clear();
        final var loadFromDb = sut.findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc(imdbId);

        // Assert
        assertThat(loadFromDb).contains(entry2);
    }

    @Test
    @Transactional
    void findByImdbId_doesntFindInvalidated() {
        // Arrange
        final var imdbId = ImdbId.of("tt0123755");
        final var timestamp = Instant.now();
        final var entry = new QueryMeta(null, imdbId, timestamp, null, true, List.of());

        // Act
        sut.save(entry);
        final var loadFromDb = sut.findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc(imdbId);

        // Assert
        assertThat(loadFromDb).isEmpty();
    }

    @Test
    @Transactional
    void findByImdbIdIn_includesInvalidatedRows() {
        // Arrange: tt1 has a valid row, tt2 only an invalidated one — findByImdbIdIn (unlike the
        // AndInvalidatedIsFalse variant) must still surface tt2 (e.g. for "last scraped at").
        final var timestamp = Instant.now();
        sut.save(new QueryMeta(null, ImdbId.of("tt1"), timestamp, null, false, List.of()));
        sut.save(new QueryMeta(null, ImdbId.of("tt2"), timestamp, null, true, List.of()));
        entityManager.flush();
        entityManager.clear();

        // Act
        final var found = sut.findByImdbIdIn(List.of(ImdbId.of("tt1"), ImdbId.of("tt2")));

        // Assert
        assertThat(found).extracting(QueryMeta::getImdbId).containsExactlyInAnyOrder(ImdbId.of("tt1"), ImdbId.of("tt2"));
    }

    @Test
    @Transactional
    void invalidateByImdbIds_marksRowsAndHidesThem() {
        // Arrange
        final var timestamp = Instant.now();
        sut.save(new QueryMeta(null, ImdbId.of("tt1"), timestamp, null, false, List.of()));
        sut.save(new QueryMeta(null, ImdbId.of("tt2"), timestamp, null, false, List.of()));
        entityManager.flush();
        entityManager.clear();

        // Act
        final int affected = sut.invalidateByImdbIds(List.of(ImdbId.of("tt1")));
        entityManager.flush();
        entityManager.clear();

        // Assert
        assertThat(affected).isEqualTo(1);
        assertThat(sut.findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc(ImdbId.of("tt1"))).isEmpty();
        assertThat(sut.findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc(ImdbId.of("tt2"))).isPresent();
    }
}
