package tech.dobler.werstreamt.persistence;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;


@Slf4j
@DataJpaTest
public class QueryMetaRepositoryTest {
    @Autowired
    private QueryMetaRepository sut;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void setup()
    {
        sut.deleteAll();
    }

    @Test
    @Transactional
    void saveAndLoadOne() {
        // Arrange
//        final var timestamp = Instant.parse("2024-06-17T10:00:00Z");
        final var imdbId = "tt0123755";
        final var timestamp = Instant.now();
        final var entry = new QueryMeta(null, imdbId, timestamp, false, List.of());

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
        final var imdbId = "tt0123755";
        final var timestamp = Instant.now();
        final var entry = new QueryMeta(null, imdbId, timestamp, false, List.of());

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
        final var imdbId = "tt0123755";
        final var timestamp = Instant.parse("2024-06-15T10:15:30Z");
        final var entry = new QueryMeta(null, imdbId, timestamp, false, List.of());
        final var entry2 = new QueryMeta(null, imdbId, timestamp.plusSeconds(15), false, List.of());
        final var entry3 = new QueryMeta(null, imdbId, timestamp.plusSeconds(20), true, List.of());
        final var entry4 = new QueryMeta(null, imdbId, timestamp.minusSeconds(15), false, List.of());

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
        final var imdbId = "tt0123755";
        final var timestamp = Instant.now();
        final var entry = new QueryMeta(null, imdbId, timestamp, true, List.of());

        // Act
        sut.save(entry);
        final var loadFromDb = sut.findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc(imdbId);

        // Assert
        assertThat(loadFromDb).isEmpty();
    }
}
