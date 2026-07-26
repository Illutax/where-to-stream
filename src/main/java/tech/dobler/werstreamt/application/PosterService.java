package tech.dobler.werstreamt.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.dobler.werstreamt.configurations.TmdbProperties;
import tech.dobler.werstreamt.domain.ImdbId;
import tech.dobler.werstreamt.domain.PosterSize;
import tech.dobler.werstreamt.persistence.TitlePoster;
import tech.dobler.werstreamt.persistence.TitlePosterRepository;
import tech.dobler.werstreamt.services.PosterSource;
import tech.dobler.werstreamt.time.TimeService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Resolves a title's poster image, caching it per {@code imdbId} in the DB (global, shared). The
 * thumbnail is fetched on first view and the full image on first hover; both are then stored so
 * TMDB is queried at most once per title. Mirrors the resolve/fetch/save shape of
 * {@code StreamInfoService}. If the poster feature is disabled (no TMDB key), returns empty and
 * touches nothing.
 */
@Service
@RequiredArgsConstructor
public class PosterService {

    private final TitlePosterRepository repository;
    private final PosterSource posterSource;
    private final TmdbProperties properties;
    private final TimeService timeService;

    /** An image ready to serve. */
    public record Poster(byte[] bytes, String contentType) {
    }

    @Transactional
    public Optional<Poster> thumb(ImdbId imdbId) {
        return resolve(imdbId, PosterSize.THUMB);
    }

    @Transactional
    public Optional<Poster> full(ImdbId imdbId) {
        return resolve(imdbId, PosterSize.FULL);
    }

    private Optional<Poster> resolve(ImdbId imdbId, PosterSize size) {
        if (!properties.enabled()) {
            return Optional.empty();
        }
        final Instant now = timeService.now();
        TitlePoster row = repository.findByImdbId(imdbId).orElse(null);

        // Discover (or re-discover a stale negative) the poster_path.
        if (row == null || (row.getPosterPath() == null && !isNegativeFresh(row, now))) {
            final String path = posterSource.findPosterPath(imdbId).orElse(null);
            row = (row == null)
                    ? repository.save(TitlePoster.of(imdbId, path, now))
                    : refresh(row, path, now);
        }
        if (row.getPosterPath() == null) {
            return Optional.empty(); // negative cache: no poster
        }

        final byte[] cached = size == PosterSize.THUMB ? row.getThumb() : row.getFull();
        if (cached != null && cached.length > 0) {
            return Optional.of(new Poster(cached, contentTypeOf(row, size)));
        }
        final byte[] bytes = posterSource.download(row.getPosterPath(), size).orElse(null);
        if (bytes == null || bytes.length == 0) {
            return Optional.empty();
        }
        store(row, size, bytes);
        return Optional.of(new Poster(bytes, "image/jpeg"));
    }

    private TitlePoster refresh(TitlePoster row, String path, Instant now) {
        row.refresh(path, now);
        return repository.save(row);
    }

    private void store(TitlePoster row, PosterSize size, byte[] bytes) {
        if (size == PosterSize.THUMB) {
            row.setThumb(bytes, "image/jpeg");
        } else {
            row.setFull(bytes, "image/jpeg");
        }
        repository.save(row);
    }

    /** A negative ("no poster") row is honoured until its TTL passes, then TMDB is asked again. */
    private boolean isNegativeFresh(TitlePoster row, Instant now) {
        return row.getFetchedAt().plus(properties.negativeCacheDays(), ChronoUnit.DAYS).isAfter(now);
    }

    private static String contentTypeOf(TitlePoster row, PosterSize size) {
        final String ct = size == PosterSize.THUMB ? row.getThumbContentType() : row.getFullContentType();
        return ct == null ? "image/jpeg" : ct;
    }
}
