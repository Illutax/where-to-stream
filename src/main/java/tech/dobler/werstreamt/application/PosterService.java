package tech.dobler.werstreamt.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.dobler.werstreamt.configurations.PosterProperties;
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
 * thumbnail is fetched on first view and the full image on first hover; both are then stored so the
 * source is queried at most once per title. Mirrors the resolve/fetch/save shape of
 * {@code StreamInfoService}. The concrete image source (IMDb by default, or TMDB) is selected at
 * startup and injected as a {@link PosterSource}; this service is agnostic to which one it is.
 *
 * <p>{@code title_poster} has a unique {@code imdb_id}, so two concurrent first-time requests for
 * the same title (e.g. the row thumbnail and a hover for the full image) would both try to insert
 * the discovery row and one would fail the constraint. The public entry points therefore run the
 * transactional {@link #resolve} through the bean's own proxy and retry once on a
 * {@link DataIntegrityViolationException}: the retry runs in a fresh transaction and simply reads
 * the row the other request committed.
 */
@Slf4j
@Service
public class PosterService {

    private final TitlePosterRepository repository;
    private final PosterSource posterSource;
    private final PosterProperties properties;
    private final TimeService timeService;
    /** The bean's own proxy, so a retry re-enters {@link #resolve} in a new transaction. */
    private final ObjectProvider<PosterService> self;

    public PosterService(TitlePosterRepository repository, PosterSource posterSource,
                         PosterProperties properties, TimeService timeService,
                         ObjectProvider<PosterService> self) {
        this.repository = repository;
        this.posterSource = posterSource;
        this.properties = properties;
        this.timeService = timeService;
        this.self = self;
    }

    /** An image ready to serve. */
    public record Poster(byte[] bytes, String contentType) {
    }

    public Optional<Poster> thumb(ImdbId imdbId) {
        return resolveRacefree(imdbId, PosterSize.THUMB);
    }

    public Optional<Poster> full(ImdbId imdbId) {
        return resolveRacefree(imdbId, PosterSize.FULL);
    }

    /**
     * Runs {@link #resolve} through the proxy (so it is transactional even though called from within
     * the bean) and retries once if a concurrent request inserted the discovery row first.
     */
    private Optional<Poster> resolveRacefree(ImdbId imdbId, PosterSize size) {
        final PosterService proxy = self.getObject();
        try {
            return proxy.resolve(imdbId, size);
        } catch (DataIntegrityViolationException concurrentInsert) {
            log.debug("title_poster row for {} was created concurrently; retrying read", imdbId);
            return proxy.resolve(imdbId, size);
        }
    }

    @Transactional
    public Optional<Poster> resolve(ImdbId imdbId, PosterSize size) {
        final Instant now = timeService.now();
        final TitlePoster row = repository.findByImdbId(imdbId)
                .map(existing -> reDiscoverStaleNegative(existing, now))
                .orElseGet(() -> discover(imdbId, now));

        // A null poster_path is a (deliberately persisted) negative-cache marker, not an absent lookup.
        if (row.getPosterPath() == null) {
            return Optional.empty();
        }
        return cachedBytes(row, size).or(() -> downloadAndStore(row, size));
    }

    /**
     * Newly-seen title: discover the poster reference and insert the row. If a concurrent request
     * inserts the same {@code imdbId} first, this insert trips the unique constraint at commit —
     * which surfaces inside the proxied {@link #resolve} call and is retried by {@link #resolveRacefree}.
     */
    private TitlePoster discover(ImdbId imdbId, Instant now) {
        final String posterPath = posterSource.findPosterPath(imdbId).orElse(null);
        return repository.save(TitlePoster.of(imdbId, posterPath, now));
    }

    /** Re-ask the source for a negative row once its TTL has passed; otherwise leave the row as is. */
    private TitlePoster reDiscoverStaleNegative(TitlePoster row, Instant now) {
        if (row.getPosterPath() != null || isNegativeFresh(row, now)) {
            return row;
        }
        row.refresh(posterSource.findPosterPath(row.getImdbId()).orElse(null), now);
        return repository.save(row);
    }

    private Optional<Poster> cachedBytes(TitlePoster row, PosterSize size) {
        final byte[] cached = size == PosterSize.THUMB ? row.getThumb() : row.getFull();
        return cached != null && cached.length > 0
                ? Optional.of(new Poster(cached, contentTypeOf(row, size)))
                : Optional.empty();
    }

    private Optional<Poster> downloadAndStore(TitlePoster row, PosterSize size) {
        return posterSource.download(row.getPosterPath(), size)
                .filter(bytes -> bytes.length > 0)
                .map(bytes -> {
                    store(row, size, bytes);
                    return new Poster(bytes, "image/jpeg");
                });
    }

    private void store(TitlePoster row, PosterSize size, byte[] bytes) {
        if (size == PosterSize.THUMB) {
            row.setThumb(bytes, "image/jpeg");
        } else {
            row.setFull(bytes, "image/jpeg");
        }
        repository.save(row);
    }

    /** A negative ("no poster") row is honoured until its TTL passes, then the source is asked again. */
    private boolean isNegativeFresh(TitlePoster row, Instant now) {
        return row.getFetchedAt().plus(properties.negativeCacheDays(), ChronoUnit.DAYS).isAfter(now);
    }

    private static String contentTypeOf(TitlePoster row, PosterSize size) {
        final String ct = size == PosterSize.THUMB ? row.getThumbContentType() : row.getFullContentType();
        return ct == null ? "image/jpeg" : ct;
    }
}
