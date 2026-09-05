package tech.dobler.where2stream.shared.platform.api;

/**
 * Whether the active poster source requires the TMDB attribution footer the SPA shows on
 * {@code GET /api/me}.
 *
 * <p>Implemented by Title Catalog (the context that knows which poster source is active) and
 * consumed by Account &amp; Access (the context that owns {@code /api/me}). It lives in
 * {@code shared} rather than in either of them, and that placement is the point: with the interface
 * in {@code titlecatalog.port.in}, Account &amp; Access depended on Title Catalog while Title
 * Catalog depended back on Account &amp; Access through {@code CurrentUserPort} — a cycle between
 * two bounded contexts. Both edges went through published ports, so the per-context isolation rules
 * were satisfied and reported nothing: each of them checks one direction and cannot see a circle.
 *
 * <p>A flag that says "the UI must show this footer" is not an Account &amp; Access fact and not a
 * Title Catalog fact; it is a contract between them. {@code shared} is where such contracts belong,
 * and it is already exempt from the isolation rules for the same reason
 * ({@code ApiExceptionHandler} knows every context's exceptions).
 */
public interface PosterAttributionPort {

    boolean tmdbAttributionRequired();
}
