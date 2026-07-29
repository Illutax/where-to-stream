package tech.dobler.where2stream.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WatchlistDateTest {

    @Test
    void rejectsANullValue() {
        assertThatThrownBy(() -> new WatchlistDate(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsABlankValue() {
        assertThatThrownBy(() -> WatchlistDate.of("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toLocalDateParsesAnIsoValue() {
        assertThat(WatchlistDate.of("2020-01-01").toLocalDate()).contains(LocalDate.of(2020, 1, 1));
    }

    @Test
    void toLocalDateIsEmptyForANonIsoValue() {
        assertThat(WatchlistDate.of("not-a-date").toLocalDate()).isEmpty();
    }

    @Test
    void compareToOrdersChronologicallyAsText() {
        assertThat(WatchlistDate.of("2020-01-01").compareTo(WatchlistDate.of("2021-01-01"))).isNegative();
    }

    @Test
    void compareToRejectsANullArgument() {
        final var date = WatchlistDate.of("2020-01-01");

        assertThatThrownBy(() -> date.compareTo(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void toStringReturnsTheRawValue() {
        assertThat(WatchlistDate.of("2020-01-01")).hasToString("2020-01-01");
    }
}
