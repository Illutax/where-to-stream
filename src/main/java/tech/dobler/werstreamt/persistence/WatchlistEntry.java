package tech.dobler.werstreamt.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tech.dobler.werstreamt.domain.ImdbId;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/**
 * One title on a user's personal watchlist. Keyed by {@code (user_id, imdb_id)}; the mutable
 * fields (name/url/added/rated/year) are refreshed on re-import.
 */
@Entity
@Table(name = "watchlist_entry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class WatchlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "imdb_id", nullable = false)
    private ImdbId imdbId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "url")
    private String url;

    @Column(name = "added", nullable = false)
    private String added;

    @Column(name = "is_rated", nullable = false)
    private boolean rated;

    @Column(name = "release_year")
    private int year;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private WatchlistEntry(UUID userId, ImdbId imdbId, String name, URI url, String added,
                           boolean rated, int year, Instant createdAt) {
        this.userId = userId;
        this.imdbId = imdbId;
        this.name = name;
        this.url = url == null ? null : url.toString();
        this.added = added;
        this.rated = rated;
        this.year = year;
        this.createdAt = createdAt;
    }

    public static WatchlistEntry of(UUID userId, ImdbId imdbId, String name, URI url, String added,
                                    boolean rated, int year, Instant createdAt) {
        return new WatchlistEntry(userId, imdbId, name, url, added, rated, year, createdAt);
    }

    /** Refresh the mutable fields from a re-import (keeps id/userId/imdbId/createdAt). */
    public void update(String name, URI url, String added, boolean rated, int year) {
        this.name = name;
        this.url = url == null ? null : url.toString();
        this.added = added;
        this.rated = rated;
        this.year = year;
    }

    public URI urlAsUri() {
        return url == null ? null : URI.create(url);
    }
}
