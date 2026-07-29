package tech.dobler.where2stream.titlecatalog.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tech.dobler.where2stream.titlecatalog.domain.AgeRating.RatingSystem;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;

import java.time.Instant;
import java.util.UUID;

/**
 * Cached IMDb metadata for a title, keyed by {@code imdbId} and shared across all users — the
 * single result of one IMDb GraphQL fetch: the poster reference and the age rating (room for the
 * localized title later). A fetched row with all-null data is a negative-cache entry. The heavy
 * poster image bytes live separately in {@link TitlePoster}; this table is small text.
 */
@Entity
@Table(name = "title_meta")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class TitleMeta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    // Value objects can't be converted on an @Id, so imdbId is a unique column with a surrogate id.
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "imdb_id", nullable = false, unique = true))
    private ImdbId imdbId;

    @Column(name = "poster_path")
    private String posterPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "rating_system")
    private RatingSystem ratingSystem;

    @Column(name = "rating_label")
    private String ratingLabel;

    @Column(name = "german_title")
    private String germanTitle;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    private TitleMeta(ImdbId imdbId, String posterPath, RatingSystem ratingSystem, String ratingLabel,
                      String germanTitle, Instant fetchedAt) {
        this.imdbId = imdbId;
        this.posterPath = posterPath;
        this.ratingSystem = ratingSystem;
        this.ratingLabel = ratingLabel;
        this.germanTitle = germanTitle;
        this.fetchedAt = fetchedAt;
    }

    public static TitleMeta of(ImdbId imdbId, String posterPath, RatingSystem ratingSystem, String ratingLabel,
                               String germanTitle, Instant fetchedAt) {
        return new TitleMeta(imdbId, posterPath, ratingSystem, ratingLabel, germanTitle, fetchedAt);
    }

    /** Re-record the metadata (e.g. a stale row re-fetched from IMDb). */
    public void refresh(String posterPath, RatingSystem ratingSystem, String ratingLabel,
                        String germanTitle, Instant fetchedAt) {
        this.posterPath = posterPath;
        this.ratingSystem = ratingSystem;
        this.ratingLabel = ratingLabel;
        this.germanTitle = germanTitle;
        this.fetchedAt = fetchedAt;
    }
}
