package tech.dobler.where2stream.titlecatalog.port.out;

import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.titlecatalog.domain.PosterSize;

import java.util.Optional;

/**
 * Looks up a title's poster.
 * Abstracts the concrete source (currently the TMDB API) so callers like {@code PosterService}
 * do not depend on it and can be tested with a fake.
 * Every method degrades to empty on any failure (missing key, no result, network/parse error) —
 * a poster is a nice-to-have.
 */
public interface PosterPort {

    /** The TMDB {@code poster_path} for an IMDb id, or empty if there is none / the source is off. */
    Optional<String> findPosterPath(ImdbId imdbId);

    /** The pre-sized image bytes for a {@code poster_path}, or empty on failure. */
    Optional<byte[]> download(String posterPath, PosterSize size);

    /**
     * Whether {@code posterPath} looks like it was produced by this source, as opposed to a stale
     * path left over from a <em>different</em> source that used to be active (a positive
     * {@code title_poster} row is shared/global and its {@code poster_path} column is
     * source-specific — TMDB stores a relative path, IMDb a full CDN URL — but the column itself
     * doesn't record which source wrote it; see TODO-47).
     * {@link PosterService} treats an invalid path like an unresolved one and re-discovers it
     * through the currently active source instead of blindly downloading with it.
     * Default {@code true}: only overridden by sources whose valid format is cheap to recognise.
     */
    default boolean isValidPosterPath(String posterPath) {
        return true;
    }
}
