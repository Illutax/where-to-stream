package tech.dobler.where2stream.watchlist.port.in;

/** Instance-wide watchlist sizes, published for the platform's metrics and status views. */
public interface WatchlistMetricsPort {

    /**
     * @param distinctTitles distinct titles across every user's list
     * @param entries        watchlist rows in total — the same film on ten lists is ten of these
     *                       and one of the above, and that ratio is what the shared caches earn
     */
    record WatchlistMetrics(long distinctTitles, long entries) {
    }

    WatchlistMetrics metrics();

    /**
     * Just the distinct-title count, for callers that need nothing else.
     *
     * <p>It has a second reader beyond the admin dashboard: the status endpoints publish it, and
     * one of those is unauthenticated. Kept as its own method so that caller runs one aggregate
     * rather than all of them.
     */
    long countDistinctTitles();
}
