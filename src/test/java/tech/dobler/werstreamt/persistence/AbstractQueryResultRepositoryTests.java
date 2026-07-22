package tech.dobler.werstreamt.persistence;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.transaction.annotation.Transactional;
import tech.dobler.werstreamt.domain.AvailabilityType;
import tech.dobler.werstreamt.domain.Price;
import tech.dobler.werstreamt.domain.Availability;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

/**
 * Repository behaviour for {@link QueryResultRepository}, run against both H2 and a Testcontainers
 * MariaDB via the concrete subclasses.
 */
@Slf4j
public abstract class AbstractQueryResultRepositoryTests {
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
        final var queryResult = new QueryResultDB(imdbId, "Cube", false, List.of(), null);
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
        final var queryResult = new QueryResultDB(imdbId, "Cube", false, availabilities, "Deutsch");
        saveAndFlush(queryResult);

        // Act
        final var results = sut.findByImdbId(imdbId);

        // Assert
        assertThat(results).containsExactly(queryResult);
        final var result = results.getFirst();
        assertThat(result).isNotSameAs(queryResult);
        assertThat(result.getId()).isNotNull();
        assertThat(result.getLanguages()).isEqualTo("Deutsch");
        assertThat(result.getAvailabilities()).containsExactlyElementsOf(availabilities);
    }

    @Test
    @Transactional
    void saveAndLoadALongLanguagesValue() {
        // Regression: some titles list many language variants; the languages column must hold
        // more than the original varchar(255) (on MariaDB the old width threw "Data too long").
        final var imdbId = "tt2194499";
        final var longLanguages = "Deutsch, Englisch (OV), ".repeat(20).trim(); // ~460 chars
        final var queryResult = new QueryResultDB(imdbId, "Prime Video", false, List.of(), longLanguages);
        saveAndFlush(queryResult);

        final var results = sut.findByImdbId(imdbId);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getLanguages()).isEqualTo(longLanguages);
    }

    @Test
    @Transactional
    void saveAndDeleteOne() {
        // Arrange
        final var imdbId = "tt0123755";
        final var queryResult = new QueryResultDB(imdbId, "Cube", false, List.of(new Availability(AvailabilityType.RENT, null, null, new Price("15.00 €"))), null);
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
