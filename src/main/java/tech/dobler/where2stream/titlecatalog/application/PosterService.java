package tech.dobler.where2stream.titlecatalog.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.titlecatalog.domain.PosterSize;
import tech.dobler.where2stream.titlecatalog.domain.TitlePoster;
import tech.dobler.where2stream.titlecatalog.port.in.TitleCacheMaintenancePort;
import tech.dobler.where2stream.titlecatalog.port.out.TitlePosterRepository;
import tech.dobler.where2stream.titlecatalog.port.out.PosterPort;
import tech.dobler.where2stream.shared.platform.time.TimeService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Optional;

/**
 * Resolves a title's poster image, caching it per {@code imdbId} in the DB (global, shared).
 * The thumbnail is fetched on first view and the full image on first hover; both are then
 * stored so the source is queried at most once per title.
 * The concrete image source (IMDb by default, or TMDB) is selected at startup
 * and injected as a {@link PosterPort}; this service is agnostic to which.
 *
 * <p><b>No DB connection is held across the network calls.</b>
 * A cold request runs as a fast transactional cache read,
 * then the (throttled) HTTP lookup/download with <em>no transaction open</em>,
 * then a fast transactional write.
 * Holding the connection across the I/O previously pinned a Hikari connection for the whole slow fetch
 * and exhausted the pool under a burst of poster requests.
 * The short transactions run through the bean's own proxy (self-invocation would bypass it).
 * {@code title_poster.imdb_id} is unique, so a concurrent first-time insert can trip the constraint;
 * such a write is swallowed ({@link #tryStore}) — the bytes are still returned and the row
 * self-heals on the next request.
 */
@Slf4j
@Service
public class PosterService implements TitleCacheMaintenancePort {

    private final TitlePosterRepository repository;
    private final PosterPort posterSource;
    private final PosterProperties properties;
    private final TimeService timeService;
    /** The bean's own proxy, so the short read/write steps are each transactional. */
    private final ObjectProvider<PosterService> self;

    public PosterService(TitlePosterRepository repository, PosterPort posterSource,
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
        return get(imdbId, PosterSize.THUMB);
    }

    public Optional<Poster> full(ImdbId imdbId) {
        return get(imdbId, PosterSize.FULL);
    }

    @Override
    public void warmPosterThumbnails(Collection<ImdbId> imdbIds) {
        imdbIds.parallelStream().forEach(this::thumb);
    }

    private Optional<Poster> get(ImdbId imdbId, PosterSize size) {
        final PosterService tx = self.getObject();

        // 1) Fast cache read; the connection is released before any network call.
        final Cached cached = tx.readCached(imdbId, size);
        if (cached.poster() != null) {
            return Optional.of(cached.poster());
        }
        if (cached.done()) {
            return Optional.empty(); // fresh negative cache: no poster
        }

        // 2) Resolve the poster path (discovering it over the network if unknown) — no tx held.
        final String posterPath = cached.posterPath() != null ? cached.posterPath() : discover(imdbId, tx);
        if (posterPath == null) {
            return Optional.empty(); // discovered: title has no poster
        }

        // 3) Download the image (no tx held), then persist it in a short transaction.
        return posterSource.download(posterPath, size)
                .filter(bytes -> bytes.length > 0)
                .map(bytes -> {
                    tryStore(imdbId, () -> tx.storeBytes(imdbId, posterPath, size, bytes));
                    return new Poster(bytes, "image/jpeg");
                })
                .or(() -> {
                    log.debug("No {} poster bytes for {} (path {})", size, imdbId, posterPath);
                    return Optional.empty();
                });
    }

    /** Looks the poster path up over the network (no tx), persists it, and returns it (null = none). */
    private String discover(ImdbId imdbId, PosterService tx) {
        final String posterPath = posterSource.findPosterPath(imdbId).orElse(null);
        tryStore(imdbId, () -> tx.storePath(imdbId, posterPath));
        return posterPath;
    }

    /**
     * Fast, read-only cache lookup: serve the cached bytes, report a fresh negative,
     * or report that the path/bytes must be fetched.
     * Reads the {@code @Lob} bytes inside the transaction.
     */
    @Transactional(readOnly = true)
    public Cached readCached(ImdbId imdbId, PosterSize size) {
        return repository.findByImdbId(imdbId)
                .map(row -> classify(row, size))
                .orElseGet(Cached::needsDiscovery);
    }

    private Cached classify(TitlePoster row, PosterSize size) {
        if (row.getPosterPath() == null) {
            return isNegativeFresh(row, timeService.now()) ? Cached.negative() : Cached.needsDiscovery();
        }
        final byte[] bytes = size == PosterSize.THUMB ? row.getThumb() : row.getFull();
        return bytes != null && bytes.length > 0
                ? Cached.hit(new Poster(bytes, contentTypeOf(row, size)))
                : Cached.needsDownload(row.getPosterPath());
    }

    /** Persists a freshly discovered poster path (or {@code null} = negative), inserting/refreshing the row. */
    @Transactional
    public void storePath(ImdbId imdbId, String posterPath) {
        final Instant now = timeService.now();
        repository.findByImdbId(imdbId).ifPresentOrElse(
                row -> {
                    row.refresh(posterPath, now);
                    repository.save(row);
                },
                () -> repository.save(TitlePoster.of(imdbId, posterPath, now)));
    }

    /** Persists downloaded bytes for a size onto the existing (or a newly created) row. */
    @Transactional
    public void storeBytes(ImdbId imdbId, String posterPath, PosterSize size, byte[] bytes) {
        final TitlePoster row = repository.findByImdbId(imdbId)
                .orElseGet(() -> TitlePoster.of(imdbId, posterPath, timeService.now()));
        if (size == PosterSize.THUMB) {
            row.setThumb(bytes, "image/jpeg");
        } else {
            row.setFull(bytes, "image/jpeg");
        }
        repository.save(row);
    }

    /**
     * Runs a short write step, swallowing the unique-constraint violation from a concurrent
     * first-time insert of the same title (the competitor stored the same data;
     * this request still returns its bytes and the row self-heals on the next request).
     */
    private void tryStore(ImdbId imdbId, Runnable write) {
        try {
            write.run();
        } catch (DataIntegrityViolationException concurrentInsert) {
            log.debug("title_poster row for {} was written concurrently; skipping duplicate", imdbId);
        }
    }

    /** A negative ("no poster") row is honoured until its TTL passes, then the source is asked again. */
    private boolean isNegativeFresh(TitlePoster row, Instant now) {
        return row.getFetchedAt().plus(properties.negativeCacheDays(), ChronoUnit.DAYS).isAfter(now);
    }

    private static String contentTypeOf(TitlePoster row, PosterSize size) {
        final String ct = size == PosterSize.THUMB ? row.getThumbContentType() : row.getFullContentType();
        return ct == null ? "image/jpeg" : ct;
    }

    /** Outcome of the cache read: a served image, a resolved "no poster", or work still to do. */
    record Cached(Poster poster, boolean done, String posterPath) {
        static Cached hit(Poster poster) {
            return new Cached(poster, false, null);
        }

        static Cached negative() {
            return new Cached(null, true, null); // fresh negative cache
        }

        static Cached needsDownload(String posterPath) {
            return new Cached(null, false, posterPath); // path known, bytes missing
        }

        static Cached needsDiscovery() {
            return new Cached(null, false, null); // path unknown (or stale negative)
        }
    }
}
