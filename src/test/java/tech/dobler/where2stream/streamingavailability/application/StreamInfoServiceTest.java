package tech.dobler.where2stream.streamingavailability.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.where2stream.streamingavailability.adapter.out.werstreamtes.WerStreamtProperties;
import tech.dobler.where2stream.shared.domain.ImdbId;
import tech.dobler.where2stream.streamingavailability.domain.QueryResult;
import tech.dobler.where2stream.streamingavailability.domain.QueryMeta;
import tech.dobler.where2stream.streamingavailability.port.out.QueryMetaRepository;
import tech.dobler.where2stream.streamingavailability.port.out.StreamAvailabilityProvider;
import tech.dobler.where2stream.streamingavailability.domain.QueryResultDB;
import tech.dobler.where2stream.shared.time.TimeService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreamInfoServiceTest {

    private static final WerStreamtProperties PROPS = new WerStreamtProperties(
            new WerStreamtProperties.Invalidate(28), new WerStreamtProperties.RateLimit(0));
    // Fixed "now" injected through the TimeService facade — cache-freshness assertions are exact
    // and repeatable instead of relative to the wall clock.
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private StreamAvailabilityProvider streamProvider;
    @Mock
    private QueryMetaRepository queryMetaRepository;
    @Mock
    private TimeService timeService;

    private StreamInfoService service;

    @BeforeEach
    void setUp() {
        when(timeService.now()).thenReturn(NOW);
        service = new StreamInfoService(streamProvider, queryMetaRepository, PROPS, timeService);
    }

    private static ImdbId id(String imdbId) {
        return ImdbId.of(imdbId);
    }

    private static QueryMeta meta(String imdbId, Instant creationTime, String serviceName) {
        return QueryMeta.of(id(imdbId), creationTime,
                List.of(new QueryResultDB(id(imdbId), serviceName, true, List.of(), null)));
    }

    private void stubFindFirst(String imdbId, Optional<QueryMeta> result) {
        when(queryMetaRepository.findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc(id(imdbId)))
                .thenReturn(result);
    }

    @Test
    void resolveReturnsCachedResultWhenFresh() {
        stubFindFirst("tt1", Optional.of(meta("tt1", NOW, "Netflix")));

        final var result = service.resolve(id("tt1"));

        assertThat(result).extracting(QueryResult::streamingServiceName).containsExactly("Netflix");
        verifyNoInteractions(streamProvider);
        verify(queryMetaRepository, never()).save(any());
    }

    @Test
    void resolveFetchesAndCachesOnMiss() {
        stubFindFirst("tt2", Optional.empty());
        final var fetched = new QueryResult(id("tt2"), "Prime Video", false, List.of(), null);
        when(streamProvider.query(id("tt2"))).thenReturn(List.of(fetched));

        final var result = service.resolve(id("tt2"));

        assertThat(result).containsExactly(fetched);
        verify(streamProvider).query(id("tt2"));
        verify(queryMetaRepository).save(any(QueryMeta.class));
    }

    @Test
    void resolveRefetchesWhenCacheExpired() {
        stubFindFirst("tt3", Optional.of(meta("tt3", NOW.minus(40, ChronoUnit.DAYS), "Stale")));
        when(streamProvider.query(id("tt3"))).thenReturn(List.of(new QueryResult(id("tt3"), "Fresh", false, List.of(), null)));

        final var result = service.resolve(id("tt3"));

        assertThat(result).extracting(QueryResult::streamingServiceName).containsExactly("Fresh");
        verify(streamProvider).query(id("tt3"));
    }

    @Test
    void resolveForceRefreshAlwaysFetches() {
        stubFindFirst("tt4", Optional.of(meta("tt4", NOW, "Cached")));
        when(streamProvider.query(id("tt4"))).thenReturn(List.of(new QueryResult(id("tt4"), "Refreshed", false, List.of(), null)));

        final var result = service.resolve(id("tt4"), true);

        assertThat(result).extracting(QueryResult::streamingServiceName).containsExactly("Refreshed");
        verify(streamProvider).query(id("tt4"));
    }

    @Test
    void resolveAllReadsCacheInOneQueryAndFetchesOnlyMisses() {
        when(queryMetaRepository.findByImdbIdInAndInvalidatedIsFalse(List.of(id("tt1"), id("tt2"))))
                .thenReturn(List.of(meta("tt1", NOW, "Netflix")));
        // tt2 is a cache miss -> fetched individually
        when(streamProvider.query(id("tt2"))).thenReturn(List.of(new QueryResult(id("tt2"), "Prime Video", false, List.of(), null)));

        final var result = service.resolveAll(List.of(id("tt1"), id("tt2")));

        assertThat(result.get(id("tt1"))).extracting(QueryResult::streamingServiceName).containsExactly("Netflix");
        assertThat(result.get(id("tt2"))).extracting(QueryResult::streamingServiceName).containsExactly("Prime Video");
        verify(queryMetaRepository).findByImdbIdInAndInvalidatedIsFalse(List.of(id("tt1"), id("tt2")));
        verify(streamProvider).query(id("tt2"));
        verify(streamProvider, never()).query(id("tt1"));
    }
}
