package tech.dobler.werstreamt.persistence;

import org.junit.jupiter.api.Test;
import tech.dobler.werstreamt.domain.ImdbId;
import tech.dobler.werstreamt.domain.ReleaseYear;
import tech.dobler.werstreamt.domain.WatchlistDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The three {@code AttributeConverter}s share the same null-safe shape (null in, null out; value
 * in, mapped value out) — covered together since each branch is otherwise a one-liner.
 */
class ConvertersTest {

    @Test
    void releaseYearConverterRoundTripsANullValue() {
        final var converter = new ReleaseYearConverter();

        assertThat(converter)
                .extracting(c -> c.convertToDatabaseColumn(null), c -> c.convertToEntityAttribute(null))
                .containsOnlyNulls();
    }

    @Test
    void releaseYearConverterRoundTripsARealValue() {
        final var converter = new ReleaseYearConverter();

        assertThat(converter)
                .extracting(c -> c.convertToDatabaseColumn(ReleaseYear.of(1999)), c -> c.convertToEntityAttribute(1999))
                .containsExactly(1999, ReleaseYear.of(1999));
    }

    @Test
    void watchlistDateConverterRoundTripsANullValue() {
        final var converter = new WatchlistDateConverter();

        assertThat(converter)
                .extracting(c -> c.convertToDatabaseColumn(null), c -> c.convertToEntityAttribute(null))
                .containsOnlyNulls();
    }

    @Test
    void watchlistDateConverterRoundTripsARealValue() {
        final var converter = new WatchlistDateConverter();

        assertThat(converter)
                .extracting(c -> c.convertToDatabaseColumn(WatchlistDate.of("2020-01-01")),
                        c -> c.convertToEntityAttribute("2020-01-01"))
                .containsExactly("2020-01-01", WatchlistDate.of("2020-01-01"));
    }

    @Test
    void imdbIdConverterRoundTripsANullValue() {
        final var converter = new ImdbIdConverter();

        assertThat(converter)
                .extracting(c -> c.convertToDatabaseColumn(null), c -> c.convertToEntityAttribute(null))
                .containsOnlyNulls();
    }

    @Test
    void imdbIdConverterRoundTripsARealValue() {
        final var converter = new ImdbIdConverter();

        assertThat(converter)
                .extracting(c -> c.convertToDatabaseColumn(ImdbId.of("tt1")), c -> c.convertToEntityAttribute("tt1"))
                .containsExactly("tt1", ImdbId.of("tt1"));
    }
}
