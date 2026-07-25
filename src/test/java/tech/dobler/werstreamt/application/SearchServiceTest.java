package tech.dobler.werstreamt.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.werstreamt.domain.Availability;
import tech.dobler.werstreamt.domain.ImdbId;
import tech.dobler.werstreamt.domain.QueryResult;
import tech.dobler.werstreamt.services.StreamInfoService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private StreamInfoService streamInfoService;
    @InjectMocks
    private SearchService service;

    private static ImdbId id(String imdbId) {
        return ImdbId.of(imdbId);
    }

    private static QueryResult result(String imdbId) {
        return new QueryResult(id(imdbId), "Netflix", true, List.<Availability>of(), null);
    }

    @Test
    void resolveByImdbIdReturnsCachedResultsWhenPresent() {
        when(streamInfoService.resolve(id("tt1"))).thenReturn(List.of(result("tt1")));

        assertThat(service.resolveByImdbId(id("tt1"))).hasValueSatisfying(list ->
                assertThat(list).hasSize(1));
    }

    @Test
    void resolveByImdbIdIsEmptyWhenNothingAvailable() {
        when(streamInfoService.resolve(id("tt1"))).thenReturn(List.of());

        assertThat(service.resolveByImdbId(id("tt1"))).isEmpty();
    }
}
