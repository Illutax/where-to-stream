package tech.dobler.where2stream.streamingavailability.port.in;

/** Availability-cache size and freshness, published for the platform's metrics view. */
public interface AvailabilityMetricsPort {

    /**
     * @param cachedTitles titles with any availability cache row
     * @param freshTitles  titles that still have a usable row; the rest will be re-scraped
     */
    record AvailabilityMetrics(long cachedTitles, long freshTitles) {

        /** Titles whose cache has expired or been invalidated — the per-instance form of the
         *  "some data is stale" banner the dashboard shows each user. */
        public long staleTitles() {
            return cachedTitles - freshTitles;
        }
    }

    AvailabilityMetrics metrics();
}
