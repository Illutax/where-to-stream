package tech.dobler.where2stream.watchlist.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.shared.kernel.domain.ReleaseYear;
import tech.dobler.where2stream.watchlist.domain.ImdbEntry;
import tech.dobler.where2stream.watchlist.domain.WatchlistDate;
import tech.dobler.where2stream.watchlist.domain.WatchlistEntry;
import tech.dobler.where2stream.watchlist.port.out.WatchlistEntryRepository;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchlistCatalogTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private WatchlistEntryRepository repository;
    @InjectMocks
    private WatchlistCatalog catalog;

    private static ImdbId id(String imdbId) {
        return ImdbId.of(imdbId);
    }

    private static WatchlistEntry stored(String imdbId, String name, boolean rated) {
        return WatchlistEntry.of(USER, id(imdbId), name, URI.create("https://www.imdb.com/title/" + imdbId + "/"),
                WatchlistDate.of("2020-01-01"), rated, ReleaseYear.of(2020), NOW);
    }

    // Record equality asserts every mapped field at once (name, url, added, rated, year, imdbId).
    private static ImdbEntry entry(String imdbId, String name, boolean rated) {
        return new ImdbEntry(name, URI.create("https://www.imdb.com/title/" + imdbId + "/"),
                WatchlistDate.of("2020-01-01"), rated, ReleaseYear.of(2020), id(imdbId));
    }

    @Test
    void findAllForAUserMapsEveryStoredFieldOntoTheEntry() {
        when(repository.findByUserId(USER)).thenReturn(List.of(stored("tt1", "The Matrix", true)));

        assertThat(catalog.findAll(USER)).containsExactly(entry("tt1", "The Matrix", true));
    }

    @Test
    void findAllWithoutAUserSpansTheWholeTable() {
        when(repository.findAll()).thenReturn(List.of(
                stored("tt1", "The Matrix", false), stored("tt2", "The Prestige", true)));

        assertThat(catalog.findAll())
                .containsExactly(entry("tt1", "The Matrix", false), entry("tt2", "The Prestige", true));
    }

    @Test
    void findAllSeenReturnsOnlyRatedEntries() {
        when(repository.findByUserIdAndRatedTrue(USER)).thenReturn(List.of(stored("tt2", "Seen", true)));

        assertThat(catalog.findAllSeen(USER)).containsExactly(entry("tt2", "Seen", true));
    }

    @Test
    void findByImdbMapsTheHitAndStaysEmptyOnAMiss() {
        when(repository.findByUserIdAndImdbId(USER, id("tt1")))
                .thenReturn(Optional.of(stored("tt1", "The Matrix", false)));
        when(repository.findByUserIdAndImdbId(USER, id("tt404"))).thenReturn(Optional.empty());

        assertThat(catalog.findByImdb(USER, id("tt1"))).contains(entry("tt1", "The Matrix", false));
        assertThat(catalog.findByImdb(USER, id("tt404"))).isEmpty();
    }

    @Test
    void aMissingUrlStaysNullInsteadOfBlowingUpTheMapping() {
        when(repository.findByUserId(USER)).thenReturn(List.of(WatchlistEntry.of(
                USER, id("tt1"), "No URL", null, WatchlistDate.of("2020-01-01"), false, ReleaseYear.of(2020), NOW)));

        assertThat(catalog.findAll(USER)).containsExactly(new ImdbEntry(
                "No URL", null, WatchlistDate.of("2020-01-01"), false, ReleaseYear.of(2020), id("tt1")));
    }

    @Test
    void isOnWatchlistDelegatesToTheExistenceQuery() {
        when(repository.existsByUserIdAndImdbId(USER, id("tt1"))).thenReturn(true);

        assertThat(catalog.isOnWatchlist(USER, id("tt1"))).isTrue();
        assertThat(catalog.isOnWatchlist(USER, id("tt2"))).isFalse();
    }

    @Test
    void theDistinctIdListsAndTheCountPassThroughUnchanged() {
        when(repository.findDistinctImdbIds()).thenReturn(List.of(id("tt1"), id("tt2")));
        when(repository.findDistinctImdbIdsRated()).thenReturn(List.of(id("tt2")));
        when(repository.countByUserId(USER)).thenReturn(7L);

        assertThat(catalog.allDistinctImdbIds()).containsExactly(id("tt1"), id("tt2"));
        assertThat(catalog.allDistinctRatedImdbIds()).containsExactly(id("tt2"));
        assertThat(catalog.count(USER)).isEqualTo(7L);
    }
}
