package tech.dobler.werstreamt.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.werstreamt.application.dto.ManageRowDto;
import tech.dobler.werstreamt.persistence.WatchlistEntry;
import tech.dobler.werstreamt.persistence.WatchlistEntryRepository;
import tech.dobler.werstreamt.services.PreCacheService;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheManagementServiceTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private WatchlistEntryRepository watchlistEntryRepository;
    @Mock
    private PreCacheService preCacheService;
    @InjectMocks
    private CacheManagementService service;

    private static WatchlistEntry entry(String imdbId, String name, boolean rated) {
        return WatchlistEntry.of(USER, imdbId, name, URI.create("https://www.imdb.com/title/" + imdbId + "/"),
                "2020-01-01", rated, 2020, CREATED);
    }

    @Test
    void managePageSortsByNameAndFlagsUncached() {
        final var zebra = entry("tt2", "Zebra", false);
        final var apple = entry("tt1", "Apple", false);
        when(watchlistEntryRepository.findAll()).thenReturn(List.of(zebra, apple));
        when(preCacheService.findUncachedImdbIds()).thenReturn(List.of("tt2"));

        final var page = service.managePage();

        assertThat(page.needsScrapeCount()).isEqualTo(1);
        assertThat(page.rows()).extracting(ManageRowDto::name).containsExactly("Apple", "Zebra");
        assertThat(page.rows()).extracting(ManageRowDto::needsScrape).containsExactly(false, true);
    }

    @Test
    void managePageMergesDuplicateTitlesAcrossUsersRatedIfAnyUserRated() {
        // Same imdbId on two users' watchlists: one rated, one not -> merged, rated = true.
        when(watchlistEntryRepository.findAll()).thenReturn(List.of(
                entry("tt1", "Movie", false), entry("tt1", "Movie", true)));
        when(preCacheService.findUncachedImdbIds()).thenReturn(List.of());

        final var page = service.managePage();

        assertThat(page.rows()).hasSize(1);
        assertThat(page.rows().get(0).isRated()).isTrue();
    }

    @Test
    void invalidateDelegatesToPreCache() {
        when(preCacheService.invalidate(List.of("tt1"))).thenReturn(3);
        assertThat(service.invalidate(List.of("tt1")).invalidated()).isEqualTo(3);
    }

    @Test
    void invalidateNullTreatedAsEmpty() {
        when(preCacheService.invalidate(List.of())).thenReturn(0);
        assertThat(service.invalidate(null).invalidated()).isZero();
    }

    @Test
    void scrapeUncachedDelegates() {
        when(preCacheService.cacheUncached()).thenReturn(5);
        assertThat(service.scrapeUncached().scraped()).isEqualTo(5);
        verifyNoInteractions(watchlistEntryRepository);
    }

    @Test
    void cacheAllDelegates() {
        when(preCacheService.cacheAll()).thenReturn(7);
        assertThat(service.cacheAll().cached()).isEqualTo(7);
    }

    @Test
    void uncachedCountReflectsFindUncachedSize() {
        when(preCacheService.findUncachedImdbIds()).thenReturn(List.of("tt1", "tt2"));
        assertThat(service.uncachedCount().uncached()).isEqualTo(2);
    }
}
