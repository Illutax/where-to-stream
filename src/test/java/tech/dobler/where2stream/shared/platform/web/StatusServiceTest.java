package tech.dobler.where2stream.shared.platform.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.where2stream.shared.platform.time.TimeService;
import tech.dobler.where2stream.watchlist.port.in.WatchlistMetricsPort;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What this service is responsible for. How the title count avoids one query per request under
 * load is {@link tech.dobler.where2stream.shared.platform.concurrency.StaleWhileRefreshingValue}'s
 * job and is tested there — repeating those cases here would pin the same behaviour twice and let
 * it drift in one place.
 */
@ExtendWith(MockitoExtension.class)
class StatusServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Duration TITLE_COUNT_TTL = StatusService.TITLE_COUNT_TTL;

    @Mock
    private TimeService timeService;
    @Mock
    private WatchlistMetricsPort watchlistMetrics;

    private StatusService service() {
        return new StatusService(timeService, watchlistMetrics);
    }

    @Test
    void reportsServerStartFromTheClockAtConstruction() {
        when(timeService.now()).thenReturn(NOW);

        assertThat(service().status().serverStart()).isEqualTo(NOW);
    }

    @Test
    void capturesServerStartOnceAtConstructionNotPerCall() {
        when(timeService.now()).thenReturn(NOW);
        final var service = service();

        assertThat(service.status().serverStart()).isEqualTo(service.status().serverStart());
    }

    @Test
    void reportsTheTitleCountFromTheWatchlistPort() {
        when(timeService.now()).thenReturn(NOW);
        when(watchlistMetrics.countDistinctTitles()).thenReturn(42L);

        assertThat(service().status().titles()).isEqualTo(42L);
    }

    @Test
    void doesNotAskTheWatchlistOncePerRequest() {
        // The endpoint this feeds is unauthenticated, so "one query per call" is the thing that
        // must not be true. The general guarantee lives in StaleWhileRefreshingValue's tests; this
        // one only checks that the count is actually wired through it and not called directly.
        when(timeService.now()).thenReturn(NOW, NOW, NOW.plus(TITLE_COUNT_TTL).minusSeconds(1));
        when(watchlistMetrics.countDistinctTitles()).thenReturn(42L);
        final var service = service();

        assertThat(new long[]{service.status().titles(), service.status().titles()})
                .containsExactly(42L, 42L);
        verify(watchlistMetrics, times(1)).countDistinctTitles();
    }
}
