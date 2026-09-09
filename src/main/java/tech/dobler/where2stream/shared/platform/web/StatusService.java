package tech.dobler.where2stream.shared.platform.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.dobler.where2stream.shared.platform.time.TimeService;
import tech.dobler.where2stream.watchlist.port.in.WatchlistMetricsPort;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Exposes build/runtime status: the application version (from the JAR manifest), the server start
 * time (captured once at bean creation, via {@link TimeService}), and how many distinct titles this
 * instance tracks.
 *
 * <p><strong>The title count is cached, and that is a security property rather than a
 * performance one.</strong> This service feeds two endpoints, and {@code StatusController} serves
 * {@code /public/status} to anyone. One database aggregate per request on an unauthenticated path
 * is a denial-of-service surface, so the count is held in memory with the instant it was taken and
 * recomputed once it has aged past {@link #TITLE_COUNT_TTL}.
 *
 * <p><strong>Expire-then-recompute would reopen exactly that hole</strong>, which is the part worth
 * being careful about: at the moment the value expires, every concurrent request sees it as stale
 * and every one of them runs the query. So a refresh is attempted under {@link ReentrantLock#tryLock()}
 * and whoever loses the race returns the old number instead of queueing. The guarantee is therefore
 * not "at most one query per TTL" but <em>at most one count query in flight, ever</em>.
 *
 * <p>The first caller after startup pays for one query, deliberately. A single query was never the
 * problem; unbounded ones were. Priming this in the constructor would tie application startup to
 * the database answering quickly, which is a worse trade than one slow request.
 *
 * <p>"Now" comes from {@link TimeService} (ADR-0003), which is also what lets the TTL be tested by
 * moving a clock instead of sleeping.
 */
@Slf4j
@Service
public class StatusService {

    /**
     * How stale the public title count may get. Minutes, because it is a size-of-instance figure
     * that nothing decides on — a watchlist import that lands a second after a refresh is not
     * visible until the next one, and that is fine.
     */
    static final Duration TITLE_COUNT_TTL = Duration.ofMinutes(5);

    private final TimeService timeService;
    private final WatchlistMetricsPort watchlistMetrics;
    private final Instant serverStart;

    /** The last count and when it was taken; {@code null} until the first successful read. */
    private final AtomicReference<CountedAt> titleCount = new AtomicReference<>();
    private final ReentrantLock refreshLock = new ReentrantLock();

    private record CountedAt(long count, Instant takenAt) {
    }

    public StatusService(TimeService timeService, WatchlistMetricsPort watchlistMetrics) {
        this.timeService = timeService;
        this.watchlistMetrics = watchlistMetrics;
        this.serverStart = timeService.now();
    }

    public StatusDto status() {
        final var version = getClass().getPackage().getImplementationVersion();
        return new StatusDto(version, serverStart, titleCount());
    }

    private long titleCount() {
        final var now = timeService.now();
        final var current = titleCount.get();
        if (current != null && current.takenAt().plus(TITLE_COUNT_TTL).isAfter(now)) {
            return current.count();
        }
        return refreshed(current, now);
    }

    /**
     * Recomputes if this thread wins the lock; otherwise serves {@code stale}.
     *
     * <p>{@code stale} is null only before the very first read has completed, and then there is
     * nothing to serve — so that one caller blocks on the lock rather than inventing a zero, which
     * would be a wrong answer rather than an old one.
     */
    private long refreshed(CountedAt stale, Instant now) {
        if (stale == null) {
            refreshLock.lock();
        } else if (!refreshLock.tryLock()) {
            return stale.count();
        }
        try {
            // Someone may have refreshed while we waited for the lock; re-read before querying.
            final var latest = titleCount.get();
            if (latest != null && latest.takenAt().plus(TITLE_COUNT_TTL).isAfter(now)) {
                return latest.count();
            }
            final var counted = new CountedAt(watchlistMetrics.countDistinctTitles(), now);
            titleCount.set(counted);
            return counted.count();
        } finally {
            refreshLock.unlock();
        }
    }
}
