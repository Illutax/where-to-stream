package tech.dobler.where2stream.titlecatalog.port.in;

import tech.dobler.where2stream.shared.domain.ImdbId;

import java.util.Collection;

/**
 * Admin cache-maintenance operations Title Catalog exposes to whatever orchestrates the (global,
 * ADMIN-only) cache warm-up — today {@code CacheManagementService}, spanning titles and streaming
 * availability. The caller supplies which titles to warm (e.g. every distinct watchlist imdbId);
 * Title Catalog owns how that's actually done.
 */
public interface TitleCacheMaintenancePort {

    /** Warms the poster thumbnail cache for every given title (parallel, best-effort). */
    void warmPosterThumbnails(Collection<ImdbId> imdbIds);
}
