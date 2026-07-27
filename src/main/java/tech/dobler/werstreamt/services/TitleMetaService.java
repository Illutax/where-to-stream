package tech.dobler.werstreamt.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.dobler.werstreamt.configurations.PosterProperties;
import tech.dobler.werstreamt.domain.AgeRating;
import tech.dobler.werstreamt.domain.AgeRating.RatingSystem;
import tech.dobler.werstreamt.domain.ImdbId;
import tech.dobler.werstreamt.persistence.TitleMeta;
import tech.dobler.werstreamt.persistence.TitleMetaRepository;
import tech.dobler.werstreamt.services.ImdbTitleClient.ImdbTitleData;
import tech.dobler.werstreamt.time.TimeService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * The single per-title IMDb metadata cache (poster reference + age rating; room for the localized
 * title later): one {@link ImdbTitleClient} fetch feeds every consumer, so a title's poster and
 * rating cost <strong>one</strong> API call. Same connection-discipline as {@code PosterService}
 * (ADR-0011): a fast transactional cache read, the HTTP fetch with <em>no transaction open</em>, a
 * fast transactional store — never a DB connection held across the network. A positive result is
 * permanent; a "no data" result is negative-cached with a TTL; a hard fetch failure is not cached
 * (retried next time).
 */
@Slf4j
@Service
public class TitleMetaService {

    private final TitleMetaRepository repository;
    private final ImdbTitleClient client;
    private final PosterProperties properties;
    private final TimeService timeService;
    /** The bean's own proxy, so the short read/store steps are each transactional. */
    private final ObjectProvider<TitleMetaService> self;

    public TitleMetaService(TitleMetaRepository repository, ImdbTitleClient client,
                            PosterProperties properties, TimeService timeService,
                            ObjectProvider<TitleMetaService> self) {
        this.repository = repository;
        this.client = client;
        this.properties = properties;
        this.timeService = timeService;
        this.self = self;
    }

    /** Cached metadata for a title, fetching it once on a miss. Empty only on a hard fetch failure. */
    public Optional<ImdbTitleData> get(ImdbId imdbId) {
        final TitleMetaService tx = self.getObject();

        final Cached cached = tx.readCached(imdbId);
        if (cached.resolved()) {
            return Optional.of(cached.data());
        }
        // Fetch with no transaction open, then persist in a short transaction.
        final Optional<ImdbTitleData> fetched = client.fetch(imdbId);
        fetched.ifPresent(data -> tryStore(imdbId, () -> tx.store(imdbId, data)));
        return fetched;
    }

    /** The poster reference for a title (IMDb source), from the shared cache. */
    public Optional<String> posterPath(ImdbId imdbId) {
        return get(imdbId).flatMap(data -> Optional.ofNullable(data.posterUrl()));
    }

    /** The age rating for a title, from the shared cache. */
    public Optional<AgeRating> ageRating(ImdbId imdbId) {
        return get(imdbId).flatMap(data -> Optional.ofNullable(data.rating()));
    }

    /** The German title for a title, from the shared cache. */
    public Optional<String> germanTitle(ImdbId imdbId) {
        return get(imdbId).flatMap(data -> Optional.ofNullable(data.germanTitle()));
    }

    @Transactional(readOnly = true)
    public Cached readCached(ImdbId imdbId) {
        return repository.findByImdbId(imdbId)
                .map(this::classify)
                .orElseGet(Cached::needsFetch);
    }

    private Cached classify(TitleMeta row) {
        final ImdbTitleData data = toData(row);
        if (data.posterUrl() != null || data.rating() != null || data.germanTitle() != null) {
            return Cached.resolved(data); // positive result — permanent
        }
        // A "no data" row is honoured until its TTL passes, then IMDb is asked again.
        return isNegativeFresh(row, timeService.now()) ? Cached.resolved(data) : Cached.needsFetch();
    }

    @Transactional
    public void store(ImdbId imdbId, ImdbTitleData data) {
        final Instant now = timeService.now();
        final RatingSystem system = data.rating() != null ? data.rating().system() : null;
        final String label = data.rating() != null ? data.rating().label() : null;
        repository.findByImdbId(imdbId).ifPresentOrElse(
                row -> {
                    row.refresh(data.posterUrl(), system, label, data.germanTitle(), now);
                    repository.save(row);
                },
                () -> repository.save(TitleMeta.of(imdbId, data.posterUrl(), system, label, data.germanTitle(), now)));
    }

    private void tryStore(ImdbId imdbId, Runnable write) {
        try {
            write.run();
        } catch (DataIntegrityViolationException concurrentInsert) {
            log.debug("title_meta row for {} was written concurrently; skipping duplicate", imdbId);
        }
    }

    private static ImdbTitleData toData(TitleMeta row) {
        final AgeRating rating = row.getRatingSystem() != null && row.getRatingLabel() != null
                ? new AgeRating(row.getRatingSystem(), row.getRatingLabel())
                : null;
        return new ImdbTitleData(row.getPosterPath(), rating, row.getGermanTitle());
    }

    private boolean isNegativeFresh(TitleMeta row, Instant now) {
        return row.getFetchedAt().plus(properties.negativeCacheDays(), ChronoUnit.DAYS).isAfter(now);
    }

    /** Outcome of the cache read: a resolved answer (data may be empty) or "fetch needed". */
    record Cached(boolean resolved, ImdbTitleData data) {
        static Cached resolved(ImdbTitleData data) {
            return new Cached(true, data);
        }

        static Cached needsFetch() {
            return new Cached(false, null);
        }
    }
}
