package tech.dobler.where2stream.streamingavailability.application;

import tech.dobler.where2stream.streamingavailability.domain.QueryResult;

import java.util.List;

/**
 * One title's resolved streaming availability from {@link StreamInfoService#resolveAll}.
 * {@code stale} is true when {@code results} came from an existing but invalidated/expired cache
 * row served immediately while a background refresh (ADR-0016) is under way — never true for a
 * title that had no cache row at all (there, resolution stays synchronous, since there is nothing
 * cached yet to show).
 */
public record ResolvedEntry(List<QueryResult> results, boolean stale) {
}
