package tech.dobler.where2stream.watchlist.domain;

import org.junit.jupiter.api.Test;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.shared.kernel.domain.ReleaseYear;
import tech.dobler.where2stream.watchlist.domain.WatchlistDate;

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

        assertThat(entry).extracting(WatchlistEntry::getUrl, WatchlistEntry::urlAsUri).containsOnlyNulls();
    }

    @Test
    void ofStoresANonNullUrlAsAString() {
        final var uri = URI.create("https://www.imdb.com/title/tt1/");
        final var entry = WatchlistEntry.of(USER, ImdbId.of("tt1"), "The Matrix", uri,
                WatchlistDate.of("2020-01-01"), false, ReleaseYear.of(1999), NOW);

        assertThat(entry).extracting(WatchlistEntry::getUrl, WatchlistEntry::urlAsUri)
                .containsExactly(uri.toString(), uri);
    }

    @Test
    void updateCanClearThePreviouslySetUrl() {
        final var entry = WatchlistEntry.of(USER, ImdbId.of("tt1"), "The Matrix",
                URI.create("https://www.imdb.com/title/tt1/"), WatchlistDate.of("2020-01-01"), false,
                ReleaseYear.of(1999), NOW);

        entry.update("The Matrix", null, WatchlistDate.of("2021-01-01"), true, ReleaseYear.of(1999));

        assertThat(entry).extracting(WatchlistEntry::getUrl, WatchlistEntry::isRated).containsExactly(null, true);
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
