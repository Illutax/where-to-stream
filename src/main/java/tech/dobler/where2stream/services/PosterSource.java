package tech.dobler.where2stream.services;

import tech.dobler.where2stream.shared.domain.ImdbId;
import tech.dobler.where2stream.domain.PosterSize;

import java.util.Optional;

/**
 * Looks up a title's poster. Abstracts the concrete source (currently the TMDB API) so callers like
 * {@code PosterService} do not depend on it and can be tested with a fake. Every method degrades to
 * empty on any failure (missing key, no result, network/parse error) — a poster is a nice-to-have.
 */
public interface PosterSource {

    /** The TMDB {@code poster_path} for an IMDb id, or empty if there is none / the source is off. */
    Optional<String> findPosterPath(ImdbId imdbId);

    /** The pre-sized image bytes for a {@code poster_path}, or empty on failure. */
    Optional<byte[]> download(String posterPath, PosterSize size);
}
