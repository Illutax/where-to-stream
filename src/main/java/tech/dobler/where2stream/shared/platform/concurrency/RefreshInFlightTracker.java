package tech.dobler.where2stream.shared.platform.concurrency;

import org.springframework.stereotype.Component;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guards against triggering two concurrent background refreshes for the same title (ADR-0016):
 * the demand-driven path (a page request hitting a stale entry) and the scheduled job can both
 * want to refresh the same {@code imdbId} around the same time.
 */
@Component
public class RefreshInFlightTracker {

    private final Set<ImdbId> inFlight = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** @return true if {@code imdbId} was not already in flight (the caller should start a refresh). */
    public boolean tryStart(ImdbId imdbId) {
        return inFlight.add(imdbId);
    }

    public void finish(ImdbId imdbId) {
        inFlight.remove(imdbId);
    }
}
