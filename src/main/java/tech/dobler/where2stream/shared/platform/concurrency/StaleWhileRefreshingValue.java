package tech.dobler.where2stream.shared.platform.concurrency;

import tech.dobler.where2stream.shared.platform.time.TimeService;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * A value that is expensive to compute, cached for a while, and recomputed by at most one thread at
 * a time.
 *
 * <p><strong>The stampede guard is the point, not the TTL.</strong> The obvious lazy cache —
 * "expired? then recompute" — bounds the work to one computation per TTL only when requests arrive
 * one at a time. Under concurrency it does the opposite of what it promises: the moment the value
 * expires, every in-flight request sees it as stale and every one of them starts computing. On a
 * quiet page that is one slow response; on an unauthenticated endpoint it is a way to turn a burst
 * of requests into a burst of database queries.
 *
 * <p>So a refresh is attempted under {@link ReentrantLock#tryLock()}, and a thread that loses the
 * race returns the previous value instead of queueing behind the winner. The guarantee is therefore
 * not "at most one computation per TTL" but <strong>at most one computation in flight at any
 * moment</strong>, whatever the arrival pattern.
 *
 * <p>Two deliberate consequences:
 *
 * <ul>
 *   <li><strong>The first call blocks.</strong> Before anything has been computed there is no old
 *       value to serve, and serving a default would be a wrong answer rather than an old one — so
 *       that caller waits. Priming at construction is not done here: it would move the cost into
 *       application startup, where a slow dependency stops the app coming up at all.
 *   <li><strong>A failed refresh propagates</strong> and the previous value is kept. The exception
 *       is not swallowed in favour of a stale number, because a caller that silently serves an old
 *       value while its source is down reports health it cannot vouch for. Keeping the old value
 *       means the next call retries rather than starting from cold.
 * </ul>
 *
 * <p>Time comes from {@link TimeService} (ADR-0003), which is what lets the TTL be tested by moving
 * a clock instead of sleeping.
 *
 * <p>Not a Spring bean: it is a value holder, created by whoever owns the value. Nothing in this
 * project needs a cache abstraction (there is no Spring Cache or Caffeine on the classpath), and
 * pulling one in for a handful of scalars would cost more than this class.
 *
 * @param <T> the cached value; treated as immutable, since every caller shares the same instance
 */
public final class StaleWhileRefreshingValue<T> {

    private final TimeService timeService;
    private final Duration ttl;
    private final Supplier<T> compute;

    private final AtomicReference<Snapshot<T>> current = new AtomicReference<>();
    private final ReentrantLock refreshLock = new ReentrantLock();

    private record Snapshot<T>(T value, Instant takenAt) {
        boolean isFreshAt(Instant now, Duration ttl) {
            return takenAt.plus(ttl).isAfter(now);
        }
    }

    private StaleWhileRefreshingValue(TimeService timeService, Duration ttl, Supplier<T> compute) {
        this.timeService = timeService;
        this.ttl = ttl;
        this.compute = compute;
    }

    /**
     * @param ttl     how stale the value may get before a caller triggers a refresh
     * @param compute how to obtain a fresh value; called on a caller's thread, never concurrently
     *                with itself
     */
    public static <T> StaleWhileRefreshingValue<T> every(TimeService timeService, Duration ttl, Supplier<T> compute) {
        return new StaleWhileRefreshingValue<>(timeService, ttl, compute);
    }

    /** The cached value, recomputing first if it has aged past the TTL and no one else is on it. */
    public T get() {
        final var now = timeService.now();
        final var snapshot = current.get();
        if (snapshot != null && snapshot.isFreshAt(now, ttl)) {
            return snapshot.value();
        }
        return refreshed(snapshot, now);
    }

    private T refreshed(Snapshot<T> stale, Instant now) {
        if (stale == null) {
            refreshLock.lock();
        } else if (!refreshLock.tryLock()) {
            return stale.value();
        }
        try {
            // Only reachable by the first caller ever, or by a refresh winner. Both may have waited,
            // so re-read: someone else may have refreshed in the meantime.
            final var latest = current.get();
            if (latest != null && latest.isFreshAt(now, ttl)) {
                return latest.value();
            }
            final var refreshed = new Snapshot<>(compute.get(), now);
            current.set(refreshed);
            return refreshed.value();
        } finally {
            refreshLock.unlock();
        }
    }
}
