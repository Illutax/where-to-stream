package tech.dobler.where2stream.shared.platform.concurrency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.where2stream.shared.platform.time.TimeService;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaleWhileRefreshingValueTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(5);
    /** How long the burst test can hold a computation open; only a backstop against a hang. */
    private static final Duration COMPUTE_HELD_FOR = Duration.ofSeconds(30);

    @Mock
    private TimeService timeService;

    private StaleWhileRefreshingValue<Integer> valueOf(Supplier<Integer> compute) {
        return StaleWhileRefreshingValue.every(timeService, TTL, compute);
    }

    @Test
    void computesOnceAndServesTheSameValueWithinTheTtl() {
        when(timeService.now()).thenReturn(NOW, NOW.plus(TTL).minusSeconds(1));
        final var computations = new AtomicInteger();
        final var value = valueOf(computations::incrementAndGet);

        assertThat(new int[]{value.get(), value.get()}).containsExactly(1, 1);
    }

    @Test
    void recomputesOnceTheValueHasAgedPastTheTtl() {
        when(timeService.now()).thenReturn(NOW, NOW.plus(TTL).plusSeconds(1));
        final var computations = new AtomicInteger();
        final var value = valueOf(computations::incrementAndGet);

        assertThat(new int[]{value.get(), value.get()}).containsExactly(1, 2);
    }

    @Test
    void treatsTheTtlBoundaryItselfAsExpired() {
        // Exactly at takenAt + ttl the value is no longer fresh: isAfter, not !isBefore. Stated as
        // a test because an off-by-one here is invisible in every other assertion.
        when(timeService.now()).thenReturn(NOW, NOW.plus(TTL));
        final var computations = new AtomicInteger();
        final var value = valueOf(computations::incrementAndGet);

        assertThat(new int[]{value.get(), value.get()}).containsExactly(1, 2);
    }

    @Test
    void aFailedRefreshPropagatesAndLeavesTheNextCallAbleToRetry() {
        when(timeService.now()).thenReturn(NOW, NOW.plus(TTL).plusSeconds(1), NOW.plus(TTL).plusSeconds(2));
        final var calls = new AtomicInteger();
        final var value = valueOf(() -> {
            if (calls.incrementAndGet() == 2) {
                throw new IllegalStateException("source is down");
            }
            return calls.get();
        });

        assertThat(value.get()).isEqualTo(1);
        assertThatThrownBy(value::get).hasMessage("source is down");

        // The failure did not poison the cache: the previous snapshot is intact, so this retries
        // rather than starting from cold.
        assertThat(value.get()).isEqualTo(3);
    }

    /**
     * The property the class exists for.
     *
     * <p>With an expired value and many callers arriving together, a plain expire-then-recompute
     * runs one computation per caller — exactly the load a cache is supposed to prevent, and
     * invisible in any single-threaded test. Here the refresher is held inside the computation
     * while the rest arrive, so the assertion is about what the losers do: return the old value.
     */
    @Test
    void aBurstOnAnExpiredValueComputesOnceAndGivesEveryoneElseTheStaleValue() throws Exception {
        final int callers = 16;
        when(timeService.now()).thenReturn(NOW, NOW.plus(TTL).plusSeconds(1));
        final var computations = new AtomicInteger();
        final var insideCompute = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        final var value = valueOf(() -> {
            final int n = computations.incrementAndGet();
            if (n == 2) {
                insideCompute.countDown();
                awaitQuietly(release);
            }
            return n;
        });

        assertThat(value.get()).as("primed").isEqualTo(1);

        final var answers = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(callers)) {
            for (int i = 0; i < callers; i++) {
                pool.submit(() -> {
                    value.get();
                    answers.incrementAndGet();
                });
            }
            assertThat(insideCompute.await(5, TimeUnit.SECONDS)).as("a refresh started").isTrue();
            // Hold the refresher inside compute until every other caller has come and gone. Without
            // this the winner might finish before the rest even arrive, and the test would pass
            // against a naive implementation that lets them all recompute.
            assertThat(reaches(answers, callers - 1))
                    .as("the other %d callers returned while the refresh was still running", callers - 1)
                    .isTrue();
            release.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).as("all callers returned").isTrue();
        }

        assertThat(answers.get()).as("nobody was left without an answer").isEqualTo(callers);
        assertThat(computations.get())
                .as("one priming computation plus at most one refresh, however many callers arrive at once")
                .isLessThanOrEqualTo(2);
    }

    /**
     * The one case where a caller must wait: nothing has been computed yet, so there is no old
     * value to hand out and a default would be a wrong answer rather than an old one.
     */
    @Test
    void theVeryFirstCallersAllWaitForTheOneComputationRatherThanGettingADefault() throws Exception {
        final int callers = 8;
        when(timeService.now()).thenReturn(NOW);
        final var computations = new AtomicInteger();
        final var value = valueOf(() -> {
            computations.incrementAndGet();
            return 7;
        });

        final var wrong = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(callers)) {
            for (int i = 0; i < callers; i++) {
                pool.submit(() -> {
                    if (value.get() != 7) {
                        wrong.incrementAndGet();
                    }
                });
            }
            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(new int[]{wrong.get(), computations.get()})
                .as("everyone got the real value, computed once")
                .containsExactly(0, 1);
    }

    /**
     * Spins until the counter reaches {@code target}, or gives up.
     *
     * <p>Its deadline is far shorter than {@link #COMPUTE_HELD_FOR}, and that gap is what makes the
     * burst test mean anything. With both at five seconds the test passed against a naive
     * implementation: the held computation timed out a moment before this check did, the queued
     * callers were released, and the counter reached its target just in time.
     */
    private static boolean reaches(AtomicInteger counter, int target) {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (counter.get() < target && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        return counter.get() >= target;
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(COMPUTE_HELD_FOR.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
