package tech.dobler.werstreamt.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.werstreamt.application.dto.OverviewEntryDto;
import tech.dobler.werstreamt.domain.Availability;
import tech.dobler.werstreamt.domain.ImdbEntry;
import tech.dobler.werstreamt.domain.QueryResult;
import tech.dobler.werstreamt.services.ImdbCatalog;
import tech.dobler.werstreamt.services.StreamInfoService;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogOverviewServiceTest {

    @Mock
    private ImdbCatalog imdbCatalog;
    @Mock
    private StreamInfoService streamInfoService;
    @InjectMocks
    private CatalogOverviewService service;

    private static ImdbEntry entry(String imdbId, String name) {
        return new ImdbEntry(1, name, URI.create("https://www.imdb.com/title/" + imdbId + "/"),
                "2020-01-01", true, 2020, imdbId);
    }

    private static QueryResult flatrate(String imdbId, String serviceName) {
        return new QueryResult(imdbId, serviceName, true, List.<Availability>of(), null);
    }

    @Test
    void overviewBatchResolvesAllEntriesOnceAndSortsByName() {
        final var zebra = entry("tt2", "Zebra");
        final var apple = entry("tt1", "Apple");
        when(imdbCatalog.findAll()).thenReturn(List.of(zebra, apple));
        when(streamInfoService.resolveAll(List.of("tt2", "tt1"))).thenReturn(Map.of(
                "tt1", List.of(flatrate("tt1", "Netflix")),
                "tt2", List.<QueryResult>of()));

        final List<OverviewEntryDto> overview = service.overview();

        // sorted by name: Apple before Zebra
        assertThat(overview).extracting(OverviewEntryDto::name).containsExactly("Apple", "Zebra");
        // available services rendered for the resolved entry, null for the unavailable one
        assertThat(overview.get(0).services()).isEqualTo("Netflix");
        assertThat(overview.get(1).services()).isNull();

        // single batched lookup (no N+1)
        final ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.captor();
        verify(streamInfoService, times(1)).resolveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly("tt2", "tt1");
    }

    @Test
    void overviewJoinsMultipleServiceLabelsWithLanguages() {
        final var e = entry("tt1", "Movie");
        when(imdbCatalog.findAll()).thenReturn(List.of(e));
        when(streamInfoService.resolveAll(List.of("tt1"))).thenReturn(Map.of("tt1", List.of(
                new QueryResult("tt1", "Netflix", true, List.of(), null),
                new QueryResult("tt1", "Prime Video", true, List.of(), "Deutsch"))));

        assertThat(service.overview().get(0).services()).isEqualTo("Netflix, Prime Video (Deutsch)");
    }
}
