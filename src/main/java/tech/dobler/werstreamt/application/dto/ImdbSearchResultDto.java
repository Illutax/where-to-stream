package tech.dobler.werstreamt.application.dto;

import tech.dobler.werstreamt.domain.ImdbId;
import tech.dobler.werstreamt.domain.ReleaseYear;

/**
 * A single IMDb title-search hit, enriched with whether it's already on the current user's
 * watchlist (the search itself knows nothing of any particular user).
 */
public record ImdbSearchResultDto(ImdbId imdbId, String name, ReleaseYear year, boolean onWatchlist) {
}
