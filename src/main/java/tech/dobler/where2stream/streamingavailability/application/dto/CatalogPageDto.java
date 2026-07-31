package tech.dobler.where2stream.streamingavailability.application.dto;

import java.util.List;

/**
 * The catalogue overview page ({@code GET /api/catalog}): every title plus whether any of them is
 * currently served from stale cache data while a background refresh is under way (ADR-0016) — a
 * page-wide flag, not per row (YAGNI until a per-title indicator is actually needed).
 */
public record CatalogPageDto(List<OverviewEntryDto> entries, boolean hasStaleEntries) {
}
