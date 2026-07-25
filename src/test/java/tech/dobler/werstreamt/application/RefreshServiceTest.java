package tech.dobler.werstreamt.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.werstreamt.persistence.WatchlistEntryRepository;
import tech.dobler.werstreamt.services.StreamInfoService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshServiceTest {

    @Mock
    private WatchlistEntryRepository watchlistEntryRepository;
    @Mock
    private StreamInfoService streamInfoService;
    @InjectMocks
    private RefreshService service;

    @Test
    void refreshSeenForceRefreshesEverySeenTitle() {
        when(watchlistEntryRepository.findDistinctImdbIdsRated()).thenReturn(List.of("tt1", "tt2"));

        assertThat(service.refreshSeen().refreshed()).isEqualTo(2);
        // force-refresh: resolve must be called with forceRefresh=true
        verify(streamInfoService).resolve("tt1", true);
        verify(streamInfoService).resolve("tt2", true);
    }

    @Test
    void refreshAllForceRefreshesEveryTitle() {
        when(watchlistEntryRepository.findDistinctImdbIds()).thenReturn(List.of("tt1", "tt2", "tt3"));

        assertThat(service.refreshAll().refreshed()).isEqualTo(3);
        verify(streamInfoService).resolve("tt3", true);
    }
}
