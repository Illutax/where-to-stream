package tech.dobler.werstreamt.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.werstreamt.domain.ImdbEntry;
import tech.dobler.werstreamt.persistence.QueryMeta;
import tech.dobler.werstreamt.persistence.QueryMetaRepository;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreCacheServiceTest {

    @Mock
    private StreamInfoService streamInfoService;
    @Mock
    private ImdbCatalog imdbCatalog;
    @Mock
    private QueryMetaRepository queryMetaRepository;
    @InjectMocks
    private PreCacheService preCacheService;

    private static ImdbEntry entry(String imdbId) {
        return new ImdbEntry(1, "name", URI.create("https://www.imdb.com/title/" + imdbId + "/"),
                "2020-01-01", false, 2020, imdbId);
    }

    @Test
    void cacheAllResolvesEveryEntryAndReturnsCount() {
        when(imdbCatalog.findAll()).thenReturn(List.of(entry("tt1"), entry("tt2"), entry("tt3")));

        final int count = preCacheService.cacheAll();

        assertThat(count).isEqualTo(3);
        verify(streamInfoService).resolve("tt1");
        verify(streamInfoService).resolve("tt2");
        verify(streamInfoService).resolve("tt3");
    }

    @Test
    void findUncachedReturnsEntriesWithoutCachedResult() {
        final var cached = entry("tt1");
        final var uncached = entry("tt2");
        when(imdbCatalog.findAll()).thenReturn(List.of(cached, uncached));
        when(queryMetaRepository.findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc("tt1"))
                .thenReturn(Optional.of(QueryMeta.of("tt1", Instant.now(), List.of())));
        when(queryMetaRepository.findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc("tt2"))
                .thenReturn(Optional.empty());

        assertThat(preCacheService.findUncached()).containsExactly(uncached);
    }

    @Test
    void cacheUncachedResolvesOnlyUncachedEntries() {
        final var cached = entry("tt1");
        final var uncached = entry("tt2");
        when(imdbCatalog.findAll()).thenReturn(List.of(cached, uncached));
        when(queryMetaRepository.findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc("tt1"))
                .thenReturn(Optional.of(QueryMeta.of("tt1", Instant.now(), List.of())));
        when(queryMetaRepository.findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc("tt2"))
                .thenReturn(Optional.empty());

        final int scraped = preCacheService.cacheUncached();

        assertThat(scraped).isEqualTo(1);
        verify(streamInfoService).resolve("tt2");
        verify(streamInfoService, never()).resolve("tt1");
    }

    @Test
    void invalidateDelegatesToRepository() {
        when(queryMetaRepository.invalidateByImdbIds(List.of("tt1", "tt2"))).thenReturn(2);

        assertThat(preCacheService.invalidate(List.of("tt1", "tt2"))).isEqualTo(2);
        verify(queryMetaRepository).invalidateByImdbIds(List.of("tt1", "tt2"));
    }

    @Test
    void invalidateWithNoSelectionIsANoop() {
        assertThat(preCacheService.invalidate(List.of())).isZero();
        verifyNoInteractions(queryMetaRepository);
    }
}
