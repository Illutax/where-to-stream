package tech.dobler.where2stream.watchlist.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tech.dobler.where2stream.shared.domain.ImdbId;
import tech.dobler.where2stream.shared.domain.ReleaseYear;
import tech.dobler.where2stream.watchlist.domain.WatchlistDate;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/**
 * One title on a user's personal watchlist.
 * Keyed by {@code (user_id, imdb_id)};
 * the mutable fields (name/url/added/rated/year) are refreshed on re-import.
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
    private WatchlistDate added;

    @Column(name = "is_rated", nullable = false)
    private boolean rated;

    @Column(name = "release_year")
    private ReleaseYear year;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private WatchlistEntry(UUID userId, ImdbId imdbId, String name, URI url, WatchlistDate added,
                           boolean rated, ReleaseYear year, Instant createdAt) {
        this.userId = userId;
        this.imdbId = imdbId;
        this.name = name;
        this.url = url == null ? null : url.toString();
        this.added = added;
        this.rated = rated;
        this.year = year;
        this.createdAt = createdAt;
    }

    public static WatchlistEntry of(UUID userId, ImdbId imdbId, String name, URI url, WatchlistDate added,
                                    boolean rated, ReleaseYear year, Instant createdAt) {
        return new WatchlistEntry(userId, imdbId, name, url, added, rated, year, createdAt);
    }

    /** Refresh the mutable fields from a re-import (keeps id/userId/imdbId/createdAt). */
    public void update(String name, URI url, WatchlistDate added, boolean rated, ReleaseYear year) {
        this.name = name;
        this.url = url == null ? null : url.toString();
        this.added = added;
        this.rated = rated;
        this.year = year;
    }

    /**
     * Mark this title as seen / not seen (the in-app toggle).
     * Note a later full-sync CSV re-import is the source of truth and can overwrite this via
     * {@link #update}.
     */
    public void markSeen(boolean seen) {
        this.rated = seen;
    }

    public URI urlAsUri() {
        return url == null ? null : URI.create(url);
    }
}
