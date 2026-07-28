package tech.dobler.werstreamt.domain;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImdbEntryTest {

    private static ImdbEntry entry(String imdbId, String added) {
        return new ImdbEntry("Title", URI.create("https://www.imdb.com/title/" + imdbId + "/"),
                WatchlistDate.of(added), false, ReleaseYear.of(2020), ImdbId.of(imdbId));
    }

    @Test
    void compareToOrdersByAddedDateDescending() {
        final var older = entry("tt1", "2020-01-01");
        final var newer = entry("tt2", "2021-01-01");

        assertThat(newer.compareTo(older)).isNegative(); // the newer entry sorts first
        assertThat(older.compareTo(newer)).isPositive();
    }

    @Test
    void compareToRejectsANullArgument() {
        final var entry = entry("tt1", "2020-01-01");

        assertThatThrownBy(() -> entry.compareTo(null)).isInstanceOf(NullPointerException.class);
    }
}
