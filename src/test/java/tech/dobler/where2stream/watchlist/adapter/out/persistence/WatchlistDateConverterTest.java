package tech.dobler.where2stream.watchlist.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import tech.dobler.where2stream.watchlist.domain.WatchlistDate;

import static org.assertj.core.api.Assertions.assertThat;

class WatchlistDateConverterTest {

    @Test
    void roundTripsANullValue() {
        final var converter = new WatchlistDateConverter();

        assertThat(converter)
                .extracting(c -> c.convertToDatabaseColumn(null), c -> c.convertToEntityAttribute(null))
                .containsOnlyNulls();
    }

    @Test
    void roundTripsARealValue() {
        final var converter = new WatchlistDateConverter();

        assertThat(converter)
                .extracting(c -> c.convertToDatabaseColumn(WatchlistDate.of("2020-01-01")),
                        c -> c.convertToEntityAttribute("2020-01-01"))
                .containsExactly("2020-01-01", WatchlistDate.of("2020-01-01"));
    }
}
