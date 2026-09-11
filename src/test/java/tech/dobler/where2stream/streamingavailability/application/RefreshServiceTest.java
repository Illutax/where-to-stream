package tech.dobler.where2stream.streamingavailability.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.watchlist.port.in.WatchlistCatalogPort;
import tech.dobler.where2stream.streamingavailability.application.StreamInfoService;

import tech.dobler.where2stream.streamingavailability.application.dto.RefreshResultDto;
import tech.dobler.where2stream.streamingavailability.domain.ScrapingException;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshServiceTest {

    @Mock
    private WatchlistCatalogPort watchlistCatalogPort;
    @Mock
    private StreamInfoService streamInfoService;
    @InjectMocks
    private RefreshService service;

    private static ImdbId id(String imdbId) {
        return ImdbId.of(imdbId);
    }

    @Test
    void refreshSeenForceRefreshesEverySeenTitle() {
        when(watchlistCatalogPort.allDistinctRatedImdbIds()).thenReturn(List.of(id("tt1"), id("tt2")));

        assertThat(service.refreshSeen()).isEqualTo(new RefreshResultDto(2, 0));
        // force-refresh: resolve must be called with forceRefresh=true
        verify(streamInfoService).resolve(id("tt1"), true);
        verify(streamInfoService).resolve(id("tt2"), true);
    }

    @Test
    void refreshAllForceRefreshesEveryTitle() {
        when(watchlistCatalogPort.allDistinctImdbIds()).thenReturn(List.of(id("tt1"), id("tt2"), id("tt3")));

        assertThat(service.refreshAll()).isEqualTo(new RefreshResultDto(3, 0));
        verify(streamInfoService).resolve(id("tt3"), true);
    }

    @Test
    void refreshContinuesPastAFailingTitleAndReportsIt() {
        when(watchlistCatalogPort.allDistinctImdbIds()).thenReturn(List.of(id("tt1"), id("ttBad"), id("tt2")));
        when(streamInfoService.resolve(id("ttBad"), true))
                .thenThrow(new ScrapingException("werstreamt.es down", new IOException("timeout")));

        assertThat(service.refreshAll()).isEqualTo(new RefreshResultDto(2, 1));
        // The failure is skipped, not fatal: the remaining titles are still refreshed.
        verify(streamInfoService).resolve(id("tt1"), true);
        verify(streamInfoService).resolve(id("tt2"), true);
    }
}
