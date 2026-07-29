package tech.dobler.where2stream.titlecatalog.port.in;

/**
 * Whether the active poster source requires the TMDB attribution footer the SPA shows on
 * {@code GET /api/me} — published so Account & Access can include it in {@code MeDto} without
 * depending on Title Catalog's {@code TmdbProperties} directly.
 */
public interface PosterAttributionPort {

    boolean tmdbAttributionRequired();
}
