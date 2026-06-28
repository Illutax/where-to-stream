package tech.dobler.werstreamt.services;

import tech.dobler.werstreamt.domain.QueryResult;

import java.util.List;

/**
 * Looks up where a title (by IMDb id) can be streamed. Abstracts the concrete source
 * (currently werstreamt.es via jsoup) so callers like {@code StreamInfoService} do not depend
 * on the scraping implementation and can be tested with a fake.
 */
public interface StreamAvailabilityProvider {

    List<QueryResult> query(String imdbId);
}
