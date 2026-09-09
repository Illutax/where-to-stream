package tech.dobler.where2stream.shared.platform.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.where2stream.shared.platform.time.TimeService;
import tech.dobler.where2stream.watchlist.port.in.WatchlistMetricsPort;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void countsTitlesOnceAndServesTheCachedValueWithinTheTtl() {
        when(timeService.now()).thenReturn(NOW, NOW, NOW.plus(TITLE_COUNT_TTL).minusSeconds(1));
        when(watchlistMetrics.countDistinctTitles()).thenReturn(42L);
        final var service = service();

        assertThat(new long[]{service.status().titles(), service.status().titles()})
                .containsExactly(42L, 42L);
        verify(watchlistMetrics, times(1)).countDistinctTitles();
    }

    @Test
    void countsAgainOnceTheValueHasAgedPastTheTtl() {
        when(timeService.now()).thenReturn(NOW, NOW, NOW.plus(TITLE_COUNT_TTL).plusSeconds(1));
        when(watchlistMetrics.countDistinctTitles()).thenReturn(42L, 43L);
        final var service = service();

        assertThat(new long[]{service.status().titles(), service.status().titles()})
                .containsExactly(42L, 43L);
    }

    /**
     * The property the cache exists for: {@code /public/status} is unauthenticated, so the bound
     * that matters is not "one query per TTL" but one query in flight at a time. With an expired
     * value and many concurrent callers, a naive expire-then-recompute runs one query per caller —
     * exactly the load the cache was added to prevent.
     */
    @Test
    void aBurstOfCallersOnAnExpiredValueRunsOneQueryAndTheRestGetTheStaleNumber() throws Exception {
        final int callers = 16;
        // Construction, the priming call, then every burst caller sees a clock past the TTL.
        when(timeService.now()).thenReturn(NOW, NOW, NOW.plus(TITLE_COUNT_TTL).plusSeconds(1));
        final var queries = new AtomicInteger();
        final var inQuery = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        when(watchlistMetrics.countDistinctTitles()).thenAnswer(invocation -> {
            if (queries.incrementAndGet() == 2) {
                inQuery.countDown();       // the refresher is inside the query...
                release.await(5, TimeUnit.SECONDS);   // ...and stays there while the others arrive
            }
            return 42L;
        });
        final var service = service();
        service.status();                  // prime the cache so there is a stale value to serve

        try (var pool = Executors.newFixedThreadPool(callers)) {
            final var results = new AtomicInteger();
            for (int i = 0; i < callers; i++) {
                pool.submit(() -> {
                    if (service.status().titles() == 42L) {
                        results.incrementAndGet();
                    }
                });
            }
            assertThat(inQuery.await(5, TimeUnit.SECONDS)).as("a refresh started").isTrue();
            release.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).as("all callers returned").isTrue();
            assertThat(results.get()).as("every caller got an answer").isEqualTo(callers);
        }

        assertThat(queries.get())
                .as("one priming query plus at most one refresh, however many callers arrive at once")
                .isLessThanOrEqualTo(2);
    }
}
