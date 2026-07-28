package tech.dobler.werstreamt.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    @Test
    void disabledWhenTheRateIsZeroOrNegative() {
        final var limiter = new RateLimiter(0);

        final long start = System.nanoTime();
        limiter.acquire();
        limiter.acquire();
        limiter.acquire();
        final long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isLessThan(50);
    }

    @Test
    void actuallyBlocksSubsequentCallsUntilTheNextSlotIsDue() {
        final var limiter = new RateLimiter(10); // one slot every 100ms
        limiter.acquire(); // the very first call never blocks (primes the next slot)

        final long start = System.nanoTime();
        limiter.acquire(); // waits ~100ms
        limiter.acquire(); // waits another ~100ms
        final long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isGreaterThanOrEqualTo(150); // ~200ms expected, generous lower margin
    }

    @Test
    void eachInstanceThrottlesIndependently() {
        final var slow = new RateLimiter(1); // 1 req/s
        final var disabled = new RateLimiter(0);
        slow.acquire(); // primes the slow instance; irrelevant to `disabled`

        final long start = System.nanoTime();
        disabled.acquire();
        disabled.acquire();
        final long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isLessThan(50); // unaffected by the other, independently-rated instance
    }
}
