package tech.dobler.werstreamt.persistence;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

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
        final var timestamp = Instant.now();
        final var entry = new QueryMeta(null, timestamp);

        // Act
        final var saveResult = sut.save(entry);
        entityManager.flush();
        entityManager.clear();
        final var loadFromDb = sut.findById(saveResult.getId());

        // Assert
        assertThat(loadFromDb).contains(entry);
    }
}
