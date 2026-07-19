package tech.dobler.werstreamt.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.werstreamt.domain.ImdbEntry;
import tech.dobler.werstreamt.services.ImdbCatalog;
import tech.dobler.werstreamt.services.StreamInfoService;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshServiceTest {

    @Mock
    private ImdbCatalog imdbCatalog;
    @Mock
    private StreamInfoService streamInfoService;
    @InjectMocks
    private RefreshService service;

    private static ImdbEntry entry(String imdbId) {
        return new ImdbEntry(1, "name", URI.create("https://www.imdb.com/title/" + imdbId + "/"),
                "2020-01-01", true, 2020, imdbId);
    }

    @Test
    void refreshSeenForceRefreshesEverySeenEntry() {
        when(imdbCatalog.findAllSeen()).thenReturn(List.of(entry("tt1"), entry("tt2")));

        assertThat(service.refreshSeen().refreshed()).isEqualTo(2);
        // force-refresh: resolve must be called with forceRefresh=true
        verify(streamInfoService).resolve("tt1", true);
        verify(streamInfoService).resolve("tt2", true);
    }

    @Test
    void refreshAllForceRefreshesTheWholeCatalogue() {
        when(imdbCatalog.findAll()).thenReturn(List.of(entry("tt1"), entry("tt2"), entry("tt3")));

        assertThat(service.refreshAll().refreshed()).isEqualTo(3);
        verify(streamInfoService).resolve("tt3", true);
    }
}
