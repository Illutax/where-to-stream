package tech.dobler.where2stream.streamingavailability.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.where2stream.streamingavailability.application.command.InvalidateCommand;
import tech.dobler.where2stream.streamingavailability.application.dto.ManageRowDto;
import tech.dobler.where2stream.streamingavailability.domain.QueryMeta;
import tech.dobler.where2stream.streamingavailability.port.out.QueryMetaRepository;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.shared.kernel.domain.ReleaseYear;
import tech.dobler.where2stream.watchlist.domain.ImdbEntry;
import tech.dobler.where2stream.watchlist.domain.WatchlistDate;
import tech.dobler.where2stream.watchlist.port.in.WatchlistCatalogPort;
import tech.dobler.where2stream.titlecatalog.port.in.TitleCacheMaintenancePort;
import tech.dobler.where2stream.streamingavailability.application.PreCacheService;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheManagementServiceTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private WatchlistCatalogPort watchlistCatalogPort;
    @Mock
    private PreCacheService preCacheService;
    @Mock
    private TitleCacheMaintenancePort titleCacheMaintenancePort;
    @Mock
    private QueryMetaRepository queryMetaRepository;
    @InjectMocks
    private CacheManagementService service;

    private static ImdbId id(String imdbId) {
        return ImdbId.of(imdbId);
    }

    private static ImdbEntry entry(String imdbId, String name, boolean rated) {
        return new ImdbEntry(name, URI.create("https://www.imdb.com/title/" + imdbId + "/"),
                WatchlistDate.of("2020-01-01"), rated, ReleaseYear.of(2020), id(imdbId));
    }

    @Test
    void managePageSortsByNameAndFlagsUncached() {
        final var zebra = entry("tt2", "Zebra", false);
        final var apple = entry("tt1", "Apple", false);
        when(watchlistCatalogPort.findAll()).thenReturn(List.of(zebra, apple));
        when(preCacheService.findUncachedImdbIds()).thenReturn(List.of(id("tt2")));

        final var page = service.managePage();

        assertThat(page.needsScrapeCount()).isEqualTo(1);
        assertThat(page.rows()).extracting(ManageRowDto::name, ManageRowDto::needsScrape)
                .containsExactly(tuple("Apple", false), tuple("Zebra", true));
    }

    @Test
    void managePageReportsLastScrapedAtEvenWhenInvalidatedAndNeverForNeverScraped() {
        final var apple = entry("tt1", "Apple", false);
        final var zebra = entry("tt2", "Zebra", false);
        when(watchlistCatalogPort.findAll()).thenReturn(List.of(apple, zebra));
        // tt2 is invalidated (needsScrape = true) but still has a creationTime from its last scrape.
        when(preCacheService.findUncachedImdbIds()).thenReturn(List.of(id("tt2")));
        final var appleScrapedAt = Instant.parse("2026-07-01T00:00:00Z");
        final var zebraScrapedAt = Instant.parse("2026-07-15T00:00:00Z");
        when(queryMetaRepository.findByImdbIdIn(Set.of(id("tt1"), id("tt2")))).thenReturn(List.of(
                QueryMeta.of(id("tt1"), appleScrapedAt, List.of()),
                QueryMeta.of(id("tt2"), zebraScrapedAt, List.of())));

        final var page = service.managePage();

        assertThat(page.rows()).extracting(ManageRowDto::name, ManageRowDto::needsScrape, ManageRowDto::lastScrapedAt)
                .containsExactlyInAnyOrder(
                        tuple("Apple", false, appleScrapedAt),
                        tuple("Zebra", true, zebraScrapedAt));
    }

    @Test
    void managePageMergesDuplicateTitlesAcrossUsersRatedIfAnyUserRated() {
        // Same imdbId on two users' watchlists: one rated, one not -> merged, rated = true.
        when(watchlistCatalogPort.findAll()).thenReturn(List.of(
                entry("tt1", "Movie", false), entry("tt1", "Movie", true)));
        when(preCacheService.findUncachedImdbIds()).thenReturn(List.of());

        final var page = service.managePage();

        assertThat(page.rows()).hasSize(1);
        assertThat(page.rows().get(0).isRated()).isTrue();
    }

    @Test
    void managePageMergeShortCircuitsWhenTheFirstSeenEntryIsAlreadyRated() {
        // Opposite order from the test above: the first-seen entry (the merge's "a") is already
        // rated, so a.rated() || b.rated() short-circuits without evaluating b.rated() at all.
        when(watchlistCatalogPort.findAll()).thenReturn(List.of(
                entry("tt1", "Movie", true), entry("tt1", "Movie", false)));
        when(preCacheService.findUncachedImdbIds()).thenReturn(List.of());

        final var page = service.managePage();

        assertThat(page.rows()).hasSize(1);
        assertThat(page.rows().get(0).isRated()).isTrue();
    }

    @Test
    void managePageMergeStaysUnratedWhenNeitherEntryIsRated() {
        // a.rated() is false (evaluated), forcing b.rated() to be evaluated too (also false).
        when(watchlistCatalogPort.findAll()).thenReturn(List.of(
                entry("tt1", "Movie", false), entry("tt1", "Movie", false)));
        when(preCacheService.findUncachedImdbIds()).thenReturn(List.of());

        final var page = service.managePage();

        assertThat(page.rows()).hasSize(1);
        assertThat(page.rows().get(0).isRated()).isFalse();
    }

    @Test
    void invalidateDelegatesToPreCache() {
        when(preCacheService.invalidate(List.of(id("tt1")))).thenReturn(3);
        assertThat(service.invalidate(new InvalidateCommand(List.of(id("tt1")))).invalidated()).isEqualTo(3);
    }

    @Test
    void invalidateNullTreatedAsEmpty() {
        when(preCacheService.invalidate(List.of())).thenReturn(0);
        assertThat(service.invalidate(new InvalidateCommand(null)).invalidated()).isZero();
    }

    @Test
    void scrapeUncachedDelegates() {
        when(preCacheService.cacheUncached()).thenReturn(5);
        assertThat(service.scrapeUncached().scraped()).isEqualTo(5);
        verifyNoInteractions(watchlistCatalogPort);
    }

    @Test
    void cacheAllDelegates() {
        when(preCacheService.cacheAll()).thenReturn(7);
        assertThat(service.cacheAll().cached()).isEqualTo(7);
    }

    @Test
    void uncachedCountReflectsFindUncachedSize() {
        when(preCacheService.findUncachedImdbIds()).thenReturn(List.of(id("tt1"), id("tt2")));
        assertThat(service.uncachedCount().uncached()).isEqualTo(2);
    }
}
