package tech.dobler.where2stream.shared.platform.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tech.dobler.where2stream.accountaccess.port.in.UserMetricsPort;
import tech.dobler.where2stream.streamingavailability.port.in.AvailabilityMetricsPort;
import tech.dobler.where2stream.titlecatalog.port.in.TitleCatalogMetricsPort;
import tech.dobler.where2stream.watchlist.port.in.WatchlistMetricsPort;

/**
 * Composes the instance metrics from the four bounded contexts that own them.
 *
 * <p>Nothing is counted here. Each context publishes its own figures through its {@code port.in},
 * because how a title is counted is that context's business — "a title" means distinct
 * {@code imdbId} to Watchlist and one cache row to Title Catalog, and only they can say so.
 * The alternative, one query joining six tables from this package, would put that knowledge in the
 * one place that has no claim to it.
 *
 * <p>Uncached on purpose: this feeds the ADMIN dashboard, which is low-traffic and where an
 * operator wants the current number. The one figure that is cached is the title count on the
 * public status probe — see {@link StatusService}.
 */
@Service
@RequiredArgsConstructor
public class InstanceMetricsService {

    private final UserMetricsPort userMetrics;
    private final WatchlistMetricsPort watchlistMetrics;
    private final TitleCatalogMetricsPort titleCatalogMetrics;
    private final AvailabilityMetricsPort availabilityMetrics;

    public InstanceMetricsDto metrics() {
        final var watchlist = watchlistMetrics.metrics();
        final var catalog = titleCatalogMetrics.metrics();
        final var availability = availabilityMetrics.metrics();
        return new InstanceMetricsDto(
                userMetrics.countUsers(),
                watchlist.distinctTitles(),
                watchlist.entries(),
                catalog.metadataRows(),
                catalog.metadataWithoutData(),
                catalog.posterRows(),
                catalog.postersWithImage(),
                availability.cachedTitles(),
                availability.staleTitles());
    }
}
