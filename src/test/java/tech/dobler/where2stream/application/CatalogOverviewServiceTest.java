package tech.dobler.where2stream.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.where2stream.application.dto.OverviewEntryDto;
import tech.dobler.where2stream.domain.Availability;
import tech.dobler.where2stream.watchlist.domain.ImdbEntry;
import tech.dobler.where2stream.shared.domain.ImdbId;
import tech.dobler.where2stream.shared.domain.ReleaseYear;
import tech.dobler.where2stream.watchlist.domain.WatchlistDate;
import tech.dobler.where2stream.domain.QueryResult;
import tech.dobler.where2stream.services.StreamInfoService;
import tech.dobler.where2stream.watchlist.port.in.WatchlistCatalogPort;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogOverviewServiceTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private WatchlistCatalogPort watchlistCatalogPort;
    @Mock
    private StreamInfoService streamInfoService;
    @InjectMocks
    private CatalogOverviewService service;

    private static ImdbId id(String imdbId) {
        return ImdbId.of(imdbId);
    }

    private static ImdbEntry entry(String imdbId, String name) {
        return new ImdbEntry(name, URI.create("https://www.imdb.com/title/" + imdbId + "/"),
                WatchlistDate.of("2020-01-01"), true, ReleaseYear.of(2020), id(imdbId));
    }

    private static QueryResult flatrate(String imdbId, String serviceName) {
        return new QueryResult(id(imdbId), serviceName, true, List.<Availability>of(), null);
    }

    @Test
    void overviewBatchResolvesAllEntriesOnceAndSortsByName() {
        final var zebra = entry("tt2", "Zebra");
        final var apple = entry("tt1", "Apple");
        when(watchlistCatalogPort.findAll(USER)).thenReturn(List.of(zebra, apple));
        when(streamInfoService.resolveAll(List.of(id("tt2"), id("tt1")))).thenReturn(Map.of(
                id("tt1"), List.of(flatrate("tt1", "Netflix")),
                id("tt2"), List.<QueryResult>of()));

        final List<OverviewEntryDto> overview = service.overview(USER);

        // sorted by name: Apple before Zebra; available services rendered for the resolved entry,
        // null for the unavailable one.
        assertThat(overview).extracting(OverviewEntryDto::name, OverviewEntryDto::services)
                .containsExactly(tuple("Apple", "Netflix"), tuple("Zebra", null));

        // single batched lookup (no N+1)
        final ArgumentCaptor<Collection<ImdbId>> captor = ArgumentCaptor.captor();
        verify(streamInfoService, times(1)).resolveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(id("tt2"), id("tt1"));
    }

    @Test
    void overviewJoinsMultipleServiceLabelsWithLanguages() {
        final var e = entry("tt1", "Movie");
        when(watchlistCatalogPort.findAll(USER)).thenReturn(List.of(e));
        when(streamInfoService.resolveAll(List.of(id("tt1")))).thenReturn(Map.of(id("tt1"), List.of(
                new QueryResult(id("tt1"), "Netflix", true, List.of(), null),
                new QueryResult(id("tt1"), "Prime Video", true, List.of(), "Deutsch"))));

        assertThat(service.overview(USER).get(0).services()).isEqualTo("Netflix, Prime Video (Deutsch)");
    }
}
