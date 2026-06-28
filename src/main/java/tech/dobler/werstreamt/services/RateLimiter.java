package tech.dobler.werstreamt.services;

import org.springframework.stereotype.Component;
import tech.dobler.werstreamt.configurations.WerStreamtProperties;

import java.util.concurrent.TimeUnit;

/**
 * Simple global throttle for outbound werstreamt.es requests. {@link #acquire()} blocks the
 * calling thread until the next request slot is due, spacing requests at least
 * {@code 1 / requestsPerSecond} apart. It is {@code synchronized}, so it also throttles the
 * parallel pre-cache/refresh runs (which call it from many threads at once). A configured
 * rate of {@code <= 0} disables throttling.
 */
@Component
public class RateLimiter {

    private final long minIntervalNanos;
    private long nextAllowedNanos;

    public RateLimiter(WerStreamtProperties properties) {
        final double requestsPerSecond = properties.rateLimit().requestsPerSecond();
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
            sleep(nextAllowedNanos - now);
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
