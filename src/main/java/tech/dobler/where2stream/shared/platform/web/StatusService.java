package tech.dobler.where2stream.shared.platform.web;

import org.springframework.stereotype.Service;
import tech.dobler.where2stream.shared.platform.concurrency.StaleWhileRefreshingValue;
import tech.dobler.where2stream.shared.platform.time.TimeService;
import tech.dobler.where2stream.watchlist.port.in.WatchlistMetricsPort;

import java.time.Duration;
import java.time.Instant;

/**
 * Exposes build/runtime status: the application version (from the JAR manifest), the server start
 * time (captured once at bean creation, via {@link TimeService}), and how many distinct titles this
 * instance tracks.
 *
 * <p><strong>The title count is cached, and that is a security property rather than a performance
 * one.</strong> This service feeds two endpoints, and {@code StatusController} serves
 * {@code /public/status} to anyone. One database aggregate per request on an unauthenticated path
 * is a denial-of-service surface. How the caching avoids becoming one itself — a plain
 * expire-then-recompute would let a burst of callers all recompute at once — is
 * {@link StaleWhileRefreshingValue}'s business, not this class's.
 */
@Service
public class StatusService {

    /**
     * How stale the public title count may get. Minutes, because it is a size-of-instance figure
     * that nothing decides on — a watchlist import landing a second after a refresh is not visible
     * until the next one, and that is fine.
     */
    static final Duration TITLE_COUNT_TTL = Duration.ofMinutes(5);

    private final Instant serverStart;
    private final StaleWhileRefreshingValue<Long> titleCount;

    public StatusService(TimeService timeService, WatchlistMetricsPort watchlistMetrics) {
        this.serverStart = timeService.now();
        this.titleCount = StaleWhileRefreshingValue.every(
                timeService, TITLE_COUNT_TTL, watchlistMetrics::countDistinctTitles);
    }

    public StatusDto status() {
        final var version = getClass().getPackage().getImplementationVersion();
        return new StatusDto(version, serverStart, titleCount.get());
    }
}
