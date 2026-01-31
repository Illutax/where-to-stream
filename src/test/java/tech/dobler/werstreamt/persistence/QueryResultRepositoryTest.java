package tech.dobler.werstreamt.persistence;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.transaction.annotation.Transactional;
import tech.dobler.werstreamt.domainvalues.AvailabilityType;
import tech.dobler.werstreamt.domainvalues.Price;
import tech.dobler.werstreamt.entities.Availability;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@Slf4j
@DataJpaTest
public class QueryResultRepositoryTest {
    @Autowired
    private QueryResultRepository sut;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void setup()
    {
        sut.deleteAll();
    }

    @Test
    @Transactional
    void saveAndLoadOneWithoutAvailabilities() {
        // Arrange
        final var imdbId = "tt0123755";
        final var queryResult = new QueryResultDB(imdbId, "Cube", false, List.of());
        saveAndFlush(queryResult);

        // Act
        final var results = sut.findByImdbId(imdbId);

        // Assert
        assertThat(results).containsExactly(queryResult);
        final var result = results.getFirst();
        assertThat(result).isNotSameAs(queryResult);
        assertThat(result.getId()).isNotNull();
    }

    @Test
    @Transactional
    void saveAndLoadOneWithAvailabilities() {
        // Arrange
        final var imdbId = "tt0123755";
        final var availabilities = List.of(new Availability(AvailabilityType.RENT, null, null, new Price("15.00 €")));
        final var queryResult = new QueryResultDB(imdbId, "Cube", false, availabilities);
        saveAndFlush(queryResult);

        // Act
        final var results = sut.findByImdbId(imdbId);

        // Assert
        assertThat(results).containsExactly(queryResult);
        final var result = results.getFirst();
        assertThat(result).isNotSameAs(queryResult);
        assertThat(result.getId()).isNotNull();
        assertThat(result.getAvailabilities()).containsExactlyElementsOf(availabilities);
    }

    @Test
    @Transactional
    void saveAndDeleteOne() {
        // Arrange
        final var imdbId = "tt0123755";
        final var queryResult = new QueryResultDB(imdbId, "Cube", false, List.of(new Availability(AvailabilityType.RENT, null, null, new Price("15.00 €"))));
        log.info("Created query result: {}", queryResult);
        saveAndFlush(queryResult);
        final var sanityCheck = sut.findByImdbId(imdbId);
        log.info("Loaded query result: {}", queryResult);
        final var idToDelete = Objects.requireNonNull(queryResult.getId());
        assertThat(sanityCheck).containsExactly(queryResult);

        // Act
        log.info("Deleting query result: {}", idToDelete);
        sut.deleteById(idToDelete);
        flushAndClear();

        // Assert
        final var results = sut.findByImdbId(imdbId);
        log.info("Loaded query result: {}", queryResult);
        final var results2 = sut.findById(idToDelete);
        log.info("Loaded query result: {}", results2);
        assertThat(results).isEmpty();
        assertThat(results2).isEmpty();

    }

    private void saveAndFlush(QueryResultDB queryResult) {
        sut.save(queryResult);
        flushAndClear();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
