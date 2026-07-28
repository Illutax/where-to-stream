package tech.dobler.werstreamt.persistence;

import org.junit.jupiter.api.Test;
import tech.dobler.werstreamt.domain.ImdbId;
import tech.dobler.werstreamt.domain.ReleaseYear;
import tech.dobler.werstreamt.domain.WatchlistDate;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WatchlistEntryTest {

    private static final UUID USER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void ofStoresANullUrlAsIs() {
        final var entry = WatchlistEntry.of(USER, ImdbId.of("tt1"), "The Matrix", null,
                WatchlistDate.of("2020-01-01"), false, ReleaseYear.of(1999), NOW);

        assertThat(entry.getUrl()).isNull();
        assertThat(entry.urlAsUri()).isNull();
    }

    @Test
    void ofStoresANonNullUrlAsAString() {
        final var uri = URI.create("https://www.imdb.com/title/tt1/");
        final var entry = WatchlistEntry.of(USER, ImdbId.of("tt1"), "The Matrix", uri,
                WatchlistDate.of("2020-01-01"), false, ReleaseYear.of(1999), NOW);

        assertThat(entry.getUrl()).isEqualTo(uri.toString());
        assertThat(entry.urlAsUri()).isEqualTo(uri);
    }

    @Test
    void updateCanClearThePreviouslySetUrl() {
        final var entry = WatchlistEntry.of(USER, ImdbId.of("tt1"), "The Matrix",
                URI.create("https://www.imdb.com/title/tt1/"), WatchlistDate.of("2020-01-01"), false,
                ReleaseYear.of(1999), NOW);

        entry.update("The Matrix", null, WatchlistDate.of("2021-01-01"), true, ReleaseYear.of(1999));

        assertThat(entry.getUrl()).isNull();
        assertThat(entry.isRated()).isTrue();
    }

    @Test
    void markSeenTogglesTheRatedFlag() {
        final var entry = WatchlistEntry.of(USER, ImdbId.of("tt1"), "The Matrix", null,
                WatchlistDate.of("2020-01-01"), false, ReleaseYear.of(1999), NOW);

        entry.markSeen(true);
        assertThat(entry.isRated()).isTrue();

        entry.markSeen(false);
        assertThat(entry.isRated()).isFalse();
    }
}
