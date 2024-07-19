package tech.dobler.werstreamt.persistence;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "QeryMeta")
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
    @Column(name = "imdbId")
    private final String imdbId;
    @Column(name = "creationTime")
    private final Instant creationTime;
    @Column(name = "invalidated")
    private final boolean invalidated;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "query_meta_id")
    private final List<QueryResultDB> queries;

    public static QueryMeta of(String imdbId, Instant creationTime, List<QueryResultDB> queries) {
        return new QueryMeta(null, imdbId, creationTime, false, queries);
    }
}
