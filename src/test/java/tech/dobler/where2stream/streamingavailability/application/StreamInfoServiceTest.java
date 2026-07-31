package tech.dobler.where2stream.streamingavailability.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import tech.dobler.where2stream.shared.platform.concurrency.RefreshInFlightTracker;
import tech.dobler.where2stream.streamingavailability.adapter.out.werstreamtes.WerStreamtProperties;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.streamingavailability.domain.QueryResult;
import tech.dobler.where2stream.streamingavailability.domain.QueryMeta;
import tech.dobler.where2stream.streamingavailability.port.out.QueryMetaRepository;
import tech.dobler.where2stream.streamingavailability.port.out.StreamAvailabilityPort;
import tech.dobler.where2stream.streamingavailability.domain.QueryResultDB;
import tech.dobler.where2stream.shared.platform.time.TimeService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreamInfoServiceTest {

    private static final WerStreamtProperties PROPS = new WerStreamtProperties(
            new WerStreamtProperties.Invalidate(28, 1.5, 2.0), new WerStreamtProperties.RateLimit(0),
            new WerStreamtProperties.BackgroundRefresh(true, "0 0 4 * * *"));
    // Fixed "now" injected through the TimeService facade — cache-freshness assertions are exact
    // and repeatable instead of relative to the wall clock.
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private StreamAvailabilityPort streamProvider;
    @Mock
    private QueryMetaRepository queryMetaRepository;
    @Mock
    private TimeService timeService;
    @Mock
    private RefreshInFlightTracker refreshInFlightTracker;
    @Mock
    private ObjectProvider<StreamInfoService> self;

    private StreamInfoService service;

    // self.getObject() returns the service itself, so resolveAll's parallel-fetch/background-refresh
    // calls (proxied in prod) run directly against the mocked collaborators in the test — @Async has
    // no effect here, so refreshInBackground(...) executes synchronously and can be asserted on
    // like any other call.
    @BeforeEach
    void setUp() {
        when(timeService.now()).thenReturn(NOW);
        service = new StreamInfoService(streamProvider, queryMetaRepository, PROPS, timeService,
                refreshInFlightTracker, self);
        lenient().when(self.getObject()).thenReturn(service);
        lenient().when(refreshInFlightTracker.tryStart(any())).thenReturn(true);
    }

    private static ImdbId id(String imdbId) {
        return ImdbId.of(imdbId);
    }

    private static QueryMeta meta(String imdbId, Instant creationTime, String serviceName) {
        return QueryMeta.of(id(imdbId), creationTime,
                List.of(new QueryResultDB(id(imdbId), serviceName, true, List.of(), null)));
    }

    private static QueryMeta invalidatedMeta(String imdbId, Instant creationTime, String serviceName) {
        return new QueryMeta(UUID.randomUUID(), id(imdbId), creationTime, null, true,
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
        when(queryMetaRepository.findByImdbIdIn(List.of(id("tt1"), id("tt2"))))
                .thenReturn(List.of(meta("tt1", NOW, "Netflix")));
        // tt2 is a cache miss -> fetched individually
        when(streamProvider.query(id("tt2"))).thenReturn(List.of(new QueryResult(id("tt2"), "Prime Video", false, List.of(), null)));

        final var result = service.resolveAll(List.of(id("tt1"), id("tt2")));

        assertThat(result.get(id("tt1")).results()).extracting(QueryResult::streamingServiceName).containsExactly("Netflix");
        assertThat(result.get(id("tt1")).stale()).isFalse();
        assertThat(result.get(id("tt2")).results()).extracting(QueryResult::streamingServiceName).containsExactly("Prime Video");
        assertThat(result.get(id("tt2")).stale()).isFalse();
        verify(queryMetaRepository).findByImdbIdIn(List.of(id("tt1"), id("tt2")));
        verify(streamProvider).query(id("tt2"));
        verify(streamProvider, never()).query(id("tt1"));
    }

    @Test
    void resolveAllFetchesSeveralMissesInParallelWithoutLosingAny() {
        final var misses = List.of(id("tt1"), id("tt2"), id("tt3"), id("tt4"), id("tt5"));
        when(queryMetaRepository.findByImdbIdIn(misses)).thenReturn(List.of());
        for (final var imdbId : misses) {
            when(streamProvider.query(imdbId)).thenReturn(List.of(new QueryResult(imdbId, "Netflix", true, List.of(), null)));
        }

        final var result = service.resolveAll(misses);

        assertThat(result.keySet()).containsExactlyInAnyOrderElementsOf(misses);
        assertThat(misses).allSatisfy(imdbId -> {
            assertThat(result.get(imdbId).results()).extracting(QueryResult::streamingServiceName).containsExactly("Netflix");
            assertThat(result.get(imdbId).stale()).isFalse();
            verify(streamProvider).query(imdbId);
        });
        verify(queryMetaRepository, times(misses.size())).save(any(QueryMeta.class));
    }

    @Test
    void resolveAllServesInvalidatedRowImmediatelyAsStaleAndTriggersBackgroundRefresh() {
        when(queryMetaRepository.findByImdbIdIn(List.of(id("tt1"))))
                .thenReturn(List.of(invalidatedMeta("tt1", NOW.minus(1, ChronoUnit.DAYS), "Netflix")));
        // The background refresh (forceRefresh=true) re-fetches — stub it so it doesn't NPE.
        when(streamProvider.query(id("tt1"))).thenReturn(List.of(new QueryResult(id("tt1"), "Refreshed", false, List.of(), null)));

        final var result = service.resolveAll(List.of(id("tt1")));

        // Old (invalidated) data comes back immediately, not the refreshed data — no blocking fetch.
        assertThat(result.get(id("tt1")).stale()).isTrue();
        assertThat(result.get(id("tt1")).results()).extracting(QueryResult::streamingServiceName).containsExactly("Netflix");
        verify(refreshInFlightTracker).tryStart(id("tt1"));
        verify(refreshInFlightTracker).finish(id("tt1"));
        verify(streamProvider).query(id("tt1")); // the background refresh, not a blocking resolveAll fetch
    }

    @Test
    void resolveAllServesExpiredRowImmediatelyAsStale() {
        when(queryMetaRepository.findByImdbIdIn(List.of(id("tt1"))))
                .thenReturn(List.of(meta("tt1", NOW.minus(40, ChronoUnit.DAYS), "Stale")));
        when(streamProvider.query(id("tt1"))).thenReturn(List.of(new QueryResult(id("tt1"), "Fresh", false, List.of(), null)));

        final var result = service.resolveAll(List.of(id("tt1")));

        assertThat(result.get(id("tt1")).stale()).isTrue();
        assertThat(result.get(id("tt1")).results()).extracting(QueryResult::streamingServiceName).containsExactly("Stale");
    }

    @Test
    void resolveAllDoesNotTriggerASecondBackgroundRefreshAlreadyInFlight() {
        when(queryMetaRepository.findByImdbIdIn(List.of(id("tt1"))))
                .thenReturn(List.of(invalidatedMeta("tt1", NOW, "Netflix")));
        when(refreshInFlightTracker.tryStart(id("tt1"))).thenReturn(false); // already running

        service.resolveAll(List.of(id("tt1")));

        verifyNoInteractions(streamProvider); // no fetch: neither blocking nor a second background one
        verify(refreshInFlightTracker, never()).finish(any());
    }

    @Test
    void resolveAllNeverMarksAGenuineCacheMissAsStale() {
        when(queryMetaRepository.findByImdbIdIn(List.of(id("tt1")))).thenReturn(List.of());
        when(streamProvider.query(id("tt1"))).thenReturn(List.of(new QueryResult(id("tt1"), "Netflix", true, List.of(), null)));

        final var result = service.resolveAll(List.of(id("tt1")));

        assertThat(result.get(id("tt1")).stale()).isFalse();
    }

    @Test
    void fetchWritesADueForRefreshAtBetween1point5And2TimesAfterDays() {
        stubFindFirst("tt1", Optional.empty());
        when(streamProvider.query(id("tt1"))).thenReturn(List.of(new QueryResult(id("tt1"), "Netflix", true, List.of(), null)));
        final ArgumentCaptor<QueryMeta> captor = ArgumentCaptor.captor();

        service.resolve(id("tt1"));

        verify(queryMetaRepository).save(captor.capture());
        final var saved = captor.getValue();
        final var afterDaysSeconds = TimeUnit.DAYS.toSeconds(28);
        assertThat(saved.getDueForRefreshAt())
                .isAfterOrEqualTo(NOW.plusSeconds((long) (afterDaysSeconds * 1.5)))
                .isBefore(NOW.plusSeconds((long) (afterDaysSeconds * 2.0)));
    }
}
