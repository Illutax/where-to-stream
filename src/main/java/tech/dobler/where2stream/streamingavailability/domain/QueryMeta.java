package tech.dobler.where2stream.streamingavailability.domain;

import jakarta.persistence.*;
import lombok.*;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "QueryMeta")
@AllArgsConstructor
@NoArgsConstructor(force = true)
@Getter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class QueryMeta {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    @EqualsAndHashCode.Include
    private final UUID id;
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "imdbId"))
    private final ImdbId imdbId;
    @Column(name = "creationTime")
    private final Instant creationTime;
    // Nullable: rows written before this field existed have no due date (Phase 4's scheduled job
    // falls back to the invalidated-flag branch for those until they are next scraped).
    @Column(name = "due_for_refresh_at")
    private final Instant dueForRefreshAt;
    @Column(name = "invalidated")
    private final boolean invalidated;
    // EAGER (see ADR-0011: no OSIV); batched application-wide via
    // spring.jpa.properties.hibernate.default_batch_fetch_size (application.properties) instead
    // of an explicit @BatchSize here, so loading many QueryMeta rows at once still costs one
    // WHERE query_meta_id IN (...) round trip rather than one SELECT per row.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "query_meta_id")
    private final List<QueryResultDB> queries;

    public static QueryMeta of(ImdbId imdbId, Instant creationTime, List<QueryResultDB> queries) {
        return new QueryMeta(null, imdbId, creationTime, null, false, queries);
    }

    public static QueryMeta of(ImdbId imdbId, Instant creationTime, Instant dueForRefreshAt, List<QueryResultDB> queries) {
        return new QueryMeta(null, imdbId, creationTime, dueForRefreshAt, false, queries);
    }
}
