package tech.dobler.werstreamt.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.werstreamt.domain.Availability;
import tech.dobler.werstreamt.domain.ImdbEntry;
import tech.dobler.werstreamt.domain.QueryResult;
import tech.dobler.werstreamt.services.ImdbCatalog;
import tech.dobler.werstreamt.services.StreamAvailabilityProvider;
import tech.dobler.werstreamt.services.StreamInfoService;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private ImdbCatalog imdbCatalog;
    @Mock
    private StreamAvailabilityProvider streamProvider;
    @Mock
    private StreamInfoService streamInfoService;
    @InjectMocks
    private SearchService service;

    private static ImdbEntry entry(int id, String imdbId) {
        return new ImdbEntry(id, "name", URI.create("https://www.imdb.com/title/" + imdbId + "/"),
                "2020-01-01", false, 2020, imdbId);
    }

    private static QueryResult result(String imdbId) {
        return new QueryResult(imdbId, "Netflix", true, List.<Availability>of(), null);
    }

    @Test
    void liveQueryByCatalogIdQueriesTheProviderForAKnownId() {
        when(imdbCatalog.findById(1)).thenReturn(Optional.of(entry(1, "tt1")));
        when(streamProvider.query("tt1")).thenReturn(List.of(result("tt1")));

        assertThat(service.liveQueryByCatalogId(1)).hasValueSatisfying(list ->
                assertThat(list).extracting(QueryResult::imdbId).containsExactly("tt1"));
    }

    @Test
    void liveQueryByCatalogIdIsEmptyForUnknownId() {
        when(imdbCatalog.findById(9)).thenReturn(Optional.empty());

        assertThat(service.liveQueryByCatalogId(9)).isEmpty();
        verifyNoInteractions(streamProvider);
    }

    @Test
    void resolveByImdbIdReturnsCachedResultsWhenPresent() {
        when(streamInfoService.resolve("tt1")).thenReturn(List.of(result("tt1")));

        assertThat(service.resolveByImdbId("tt1")).hasValueSatisfying(list ->
                assertThat(list).hasSize(1));
    }

    @Test
    void resolveByImdbIdIsEmptyWhenNothingAvailable() {
        when(streamInfoService.resolve("tt1")).thenReturn(List.of());

        assertThat(service.resolveByImdbId("tt1")).isEmpty();
    }

    @Test
    void resolveByCatalogIdResolvesTheEntrysImdbId() {
        when(imdbCatalog.findById(1)).thenReturn(Optional.of(entry(1, "tt1")));
        when(streamInfoService.resolve("tt1")).thenReturn(List.of(result("tt1")));

        assertThat(service.resolveByCatalogId(1)).hasValueSatisfying(list ->
                assertThat(list).extracting(QueryResult::imdbId).containsExactly("tt1"));
    }

    @Test
    void resolveByCatalogIdIsEmptyForUnknownId() {
        when(imdbCatalog.findById(9)).thenReturn(Optional.empty());

        assertThat(service.resolveByCatalogId(9)).isEmpty();
        verifyNoInteractions(streamInfoService);
    }
}
