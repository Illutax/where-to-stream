package tech.dobler.werstreamt.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.werstreamt.persistence.QueryMeta;
import tech.dobler.werstreamt.persistence.QueryMetaRepository;
import tech.dobler.werstreamt.persistence.WatchlistEntryRepository;

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
    private WatchlistEntryRepository watchlistEntryRepository;
    @Mock
    private QueryMetaRepository queryMetaRepository;
    @InjectMocks
    private PreCacheService preCacheService;

    @Test
    void cacheAllResolvesEveryDistinctTitleAndReturnsCount() {
        when(watchlistEntryRepository.findDistinctImdbIds()).thenReturn(List.of("tt1", "tt2", "tt3"));

        final int count = preCacheService.cacheAll();

        assertThat(count).isEqualTo(3);
        verify(streamInfoService).resolve("tt1");
        verify(streamInfoService).resolve("tt2");
        verify(streamInfoService).resolve("tt3");
    }

    @Test
    void findUncachedReturnsTitlesWithoutCachedResult() {
        when(watchlistEntryRepository.findDistinctImdbIds()).thenReturn(List.of("tt1", "tt2"));
        when(queryMetaRepository.findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc("tt1"))
                .thenReturn(Optional.of(QueryMeta.of("tt1", Instant.parse("2026-01-01T00:00:00Z"), List.of())));
        when(queryMetaRepository.findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc("tt2"))
                .thenReturn(Optional.empty());

        assertThat(preCacheService.findUncachedImdbIds()).containsExactly("tt2");
    }

    @Test
    void cacheUncachedResolvesOnlyUncachedTitles() {
        when(watchlistEntryRepository.findDistinctImdbIds()).thenReturn(List.of("tt1", "tt2"));
        when(queryMetaRepository.findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc("tt1"))
                .thenReturn(Optional.of(QueryMeta.of("tt1", Instant.parse("2026-01-01T00:00:00Z"), List.of())));
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
