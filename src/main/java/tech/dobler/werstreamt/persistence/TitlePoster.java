package tech.dobler.werstreamt.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tech.dobler.werstreamt.domain.ImdbId;

import java.time.Instant;
import java.util.UUID;

/**
 * Cached poster for a title, keyed by {@code imdbId} and shared across all users (like the
 * werstreamt.es availability cache). Holds a source-specific {@code posterPath} (a TMDB path or an
 * IMDb/Amazon image URL) plus the small thumbnail and the larger hover image as BLOBs — both fetched
 * lazily (thumbnail on first view, full on first hover) and stored here so the source is hit at most
 * once per title. A {@code null posterPath} is a negative cache entry (no poster), re-checked only
 * after a TTL.
 */
@Entity
@Table(name = "title_poster")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class TitlePoster {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    // Value objects can't be converted on an @Id, so imdbId is a unique column with a surrogate id.
    @Column(name = "imdb_id", nullable = false, unique = true)
    private ImdbId imdbId;

    @Column(name = "poster_path")
    private String posterPath;

    @Lob
    @Column(name = "thumb_bytes")
    private byte[] thumb;

    @Column(name = "thumb_content_type")
    private String thumbContentType;

    @Lob
    @Column(name = "full_bytes")
    private byte[] full;

    @Column(name = "full_content_type")
    private String fullContentType;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    private TitlePoster(ImdbId imdbId, String posterPath, Instant fetchedAt) {
        this.imdbId = imdbId;
        this.posterPath = posterPath;
        this.fetchedAt = fetchedAt;
    }

    public static TitlePoster of(ImdbId imdbId, String posterPath, Instant fetchedAt) {
        return new TitlePoster(imdbId, posterPath, fetchedAt);
    }

    /** Re-record the poster_path (e.g. a previously-negative title that now has a poster). */
    public void refresh(String posterPath, Instant fetchedAt) {
        this.posterPath = posterPath;
        this.fetchedAt = fetchedAt;
        this.thumb = null;
        this.thumbContentType = null;
        this.full = null;
        this.fullContentType = null;
    }

    public void setThumb(byte[] bytes, String contentType) {
        this.thumb = bytes;
        this.thumbContentType = contentType;
    }

    public void setFull(byte[] bytes, String contentType) {
        this.full = bytes;
        this.fullContentType = contentType;
    }
}
