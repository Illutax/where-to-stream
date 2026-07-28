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

        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void releaseYearConverterRoundTripsARealValue() {
        final var converter = new ReleaseYearConverter();

        assertThat(converter.convertToDatabaseColumn(ReleaseYear.of(1999))).isEqualTo(1999);
        assertThat(converter.convertToEntityAttribute(1999)).isEqualTo(ReleaseYear.of(1999));
    }

    @Test
    void watchlistDateConverterRoundTripsANullValue() {
        final var converter = new WatchlistDateConverter();

        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void watchlistDateConverterRoundTripsARealValue() {
        final var converter = new WatchlistDateConverter();

        assertThat(converter.convertToDatabaseColumn(WatchlistDate.of("2020-01-01"))).isEqualTo("2020-01-01");
        assertThat(converter.convertToEntityAttribute("2020-01-01")).isEqualTo(WatchlistDate.of("2020-01-01"));
    }

    @Test
    void imdbIdConverterRoundTripsANullValue() {
        final var converter = new ImdbIdConverter();

        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void imdbIdConverterRoundTripsARealValue() {
        final var converter = new ImdbIdConverter();

        assertThat(converter.convertToDatabaseColumn(ImdbId.of("tt1"))).isEqualTo("tt1");
        assertThat(converter.convertToEntityAttribute("tt1")).isEqualTo(ImdbId.of("tt1"));
    }
}
