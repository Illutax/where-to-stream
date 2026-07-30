package tech.dobler.where2stream.streamingavailability.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
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
    @Column(name = "invalidated")
    private final boolean invalidated;
    // EAGER without batching means Hibernate issues one SELECT per QueryMeta row to load this
    // collection (default FetchMode.SELECT) — fine over embedded H2, but N round trips to a real
    // networked DB add up fast for a watchlist with many cached titles. @BatchSize groups those
    // into WHERE query_meta_id IN (...) chunks instead, still fully eager (ADR-0011: no OSIV).
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "query_meta_id")
    @BatchSize(size = 50)
    private final List<QueryResultDB> queries;

    public static QueryMeta of(ImdbId imdbId, Instant creationTime, List<QueryResultDB> queries) {
        return new QueryMeta(null, imdbId, creationTime, false, queries);
    }
}
