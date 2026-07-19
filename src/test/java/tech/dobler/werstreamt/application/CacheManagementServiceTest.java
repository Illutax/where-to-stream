package tech.dobler.werstreamt.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.werstreamt.application.dto.ManageRowDto;
import tech.dobler.werstreamt.domain.ImdbEntry;
import tech.dobler.werstreamt.services.ImdbCatalog;
import tech.dobler.werstreamt.services.PreCacheService;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheManagementServiceTest {

    @Mock
    private ImdbCatalog imdbCatalog;
    @Mock
    private PreCacheService preCacheService;
    @InjectMocks
    private CacheManagementService service;

    private static ImdbEntry entry(String imdbId, String name) {
        return new ImdbEntry(1, name, URI.create("https://www.imdb.com/title/" + imdbId + "/"),
                "2020-01-01", false, 2020, imdbId);
    }

    @Test
    void managePageSortsByNameAndFlagsUncached() {
        final var zebra = entry("tt2", "Zebra");
        final var apple = entry("tt1", "Apple");
        when(imdbCatalog.findAll()).thenReturn(List.of(zebra, apple));
        when(preCacheService.findUncached()).thenReturn(List.of(zebra));

        final var page = service.managePage();

        assertThat(page.needsScrapeCount()).isEqualTo(1);
        assertThat(page.rows()).extracting(ManageRowDto::name).containsExactly("Apple", "Zebra");
        assertThat(page.rows()).extracting(ManageRowDto::needsScrape).containsExactly(false, true);
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
        verifyNoInteractions(imdbCatalog);
    }

    @Test
    void cacheAllDelegates() {
        when(preCacheService.cacheAll()).thenReturn(7);
        assertThat(service.cacheAll().cached()).isEqualTo(7);
    }

    @Test
    void uncachedCountReflectsFindUncachedSize() {
        when(preCacheService.findUncached()).thenReturn(List.of(entry("tt1", "A"), entry("tt2", "B")));
        assertThat(service.uncachedCount().uncached()).isEqualTo(2);
    }
}
