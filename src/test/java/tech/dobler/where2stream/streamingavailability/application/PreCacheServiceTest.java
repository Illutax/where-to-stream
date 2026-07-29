package tech.dobler.where2stream.streamingavailability.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.streamingavailability.domain.QueryMeta;
import tech.dobler.where2stream.streamingavailability.port.out.QueryMetaRepository;
import tech.dobler.where2stream.watchlist.port.in.WatchlistCatalogPort;

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
    private WatchlistCatalogPort watchlistCatalogPort;
    @Mock
    private QueryMetaRepository queryMetaRepository;
    @InjectMocks
    private PreCacheService preCacheService;

    private static ImdbId id(String imdbId) {
        return ImdbId.of(imdbId);
    }

    @Test
    void cacheAllResolvesEveryDistinctTitleAndReturnsCount() {
        when(watchlistCatalogPort.allDistinctImdbIds()).thenReturn(List.of(id("tt1"), id("tt2"), id("tt3")));

        final int count = preCacheService.cacheAll();

        assertThat(count).isEqualTo(3);
        verify(streamInfoService).resolve(id("tt1"));
        verify(streamInfoService).resolve(id("tt2"));
        verify(streamInfoService).resolve(id("tt3"));
    }

    @Test
    void findUncachedReturnsTitlesWithoutCachedResult() {
        when(watchlistCatalogPort.allDistinctImdbIds()).thenReturn(List.of(id("tt1"), id("tt2")));
        when(queryMetaRepository.findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc(id("tt1")))
                .thenReturn(Optional.of(QueryMeta.of(id("tt1"), Instant.parse("2026-01-01T00:00:00Z"), List.of())));
        when(queryMetaRepository.findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc(id("tt2")))
                .thenReturn(Optional.empty());

        assertThat(preCacheService.findUncachedImdbIds()).containsExactly(id("tt2"));
    }

    @Test
    void cacheUncachedResolvesOnlyUncachedTitles() {
        when(watchlistCatalogPort.allDistinctImdbIds()).thenReturn(List.of(id("tt1"), id("tt2")));
        when(queryMetaRepository.findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc(id("tt1")))
                .thenReturn(Optional.of(QueryMeta.of(id("tt1"), Instant.parse("2026-01-01T00:00:00Z"), List.of())));
        when(queryMetaRepository.findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc(id("tt2")))
                .thenReturn(Optional.empty());

        final int scraped = preCacheService.cacheUncached();

        assertThat(scraped).isEqualTo(1);
        verify(streamInfoService).resolve(id("tt2"));
        verify(streamInfoService, never()).resolve(id("tt1"));
    }

    @Test
    void invalidateDelegatesToRepository() {
        when(queryMetaRepository.invalidateByImdbIds(List.of(id("tt1"), id("tt2")))).thenReturn(2);

        assertThat(preCacheService.invalidate(List.of(id("tt1"), id("tt2")))).isEqualTo(2);
        verify(queryMetaRepository).invalidateByImdbIds(List.of(id("tt1"), id("tt2")));
    }

    @Test
    void invalidateWithNoSelectionIsANoop() {
        assertThat(preCacheService.invalidate(List.of())).isZero();
        verifyNoInteractions(queryMetaRepository);
    }
}
