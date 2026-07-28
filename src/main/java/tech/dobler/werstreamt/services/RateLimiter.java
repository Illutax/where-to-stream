package tech.dobler.werstreamt.services;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * A simple per-integration outbound throttle. Each outbound integration (werstreamt.es scraping,
 * the IMDb GraphQL client, the IMDb suggestion-search client, the TMDB poster source) constructs
 * and owns exactly one instance, seeded from its own configurable requests-per-second — rates stay
 * independently controllable per integration, never shared across them. {@link #acquire()} blocks
 * the calling thread until the next request slot is due, spacing requests at least
 * {@code 1 / requestsPerSecond} apart. It is {@code synchronized}, so it also throttles concurrent
 * callers of the same instance (e.g. a parallel pre-cache/refresh run). A configured rate of
 * {@code <= 0} disables throttling.
 */
@Slf4j
public class RateLimiter {

    private final long minIntervalNanos;
    private long nextAllowedNanos;

    public RateLimiter(double requestsPerSecond) {
        this.minIntervalNanos = requestsPerSecond <= 0
                ? 0
                : (long) (TimeUnit.SECONDS.toNanos(1) / requestsPerSecond);
        this.nextAllowedNanos = System.nanoTime();
    }

    public synchronized void acquire() {
        if (minIntervalNanos == 0) {
            return;
        }
        final long now = System.nanoTime();
        if (now < nextAllowedNanos) {
            final long waitNanos = nextAllowedNanos - now;
            log.trace("Throttled outbound request by {}ms", TimeUnit.NANOSECONDS.toMillis(waitNanos));
            sleep(waitNanos);
            nextAllowedNanos += minIntervalNanos;
        } else {
            nextAllowedNanos = now + minIntervalNanos;
        }
    }

    private static void sleep(long nanos) {
        try {
            TimeUnit.NANOSECONDS.sleep(nanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
