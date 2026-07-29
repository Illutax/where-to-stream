package tech.dobler.where2stream.titlecatalog.application.dto;

import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.shared.kernel.domain.ReleaseYear;

/**
 * A single IMDb title-search hit, enriched with whether it's already on the current user's watchlist
 * (the search itself knows nothing of any particular user).
 */
public record ImdbSearchResultDto(ImdbId imdbId, String name, ReleaseYear year, boolean onWatchlist) {
}
