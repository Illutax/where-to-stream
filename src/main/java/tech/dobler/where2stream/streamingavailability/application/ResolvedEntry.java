package tech.dobler.where2stream.streamingavailability.application;

import tech.dobler.where2stream.streamingavailability.domain.QueryResult;

import java.util.List;

/**
 * One title's resolved streaming availability from {@link StreamInfoService#resolveAll}.
 * {@code stale} is true when {@code results} came from an existing but invalidated/expired cache
 * row served immediately while a background refresh (ADR-0016) is under way, or when a cache
 * miss's synchronous fetch failed (then {@code results} is empty, nothing was persisted, and the
 * next view retries). A miss that resolves normally is never stale — resolution stays
 * synchronous, since there is nothing cached yet to show.
 */
public record ResolvedEntry(List<QueryResult> results, boolean stale) {
}
