package tech.dobler.where2stream.streamingavailability.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import tech.dobler.where2stream.streamingavailability.domain.Availability;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "query_result")
@NoArgsConstructor(force = true, access = AccessLevel.PROTECTED)
@Getter
@Setter
@ToString(exclude = "availabilities")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class QueryResultDB {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    @EqualsAndHashCode.Include
    private final UUID id;
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "imdbId"))
    private final ImdbId imdbId;
    @Column(name = "title")
    private final String streamingServiceName;
    @Column(name = "flatrate")
    private final boolean flatrate;
    @Column(name = "languages")
    private final String languages;
    // Same N+1-under-EAGER as QueryMeta.queries (see its comment) — one QueryResultDB row per
    // streaming provider, each with its own availabilities collection, so this compounds with
    // that one: a watchlist of M titles with K providers each was M*K individual round trips.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "query_result_availabilities", joinColumns = @JoinColumn(name = "query_result_id"))
    @Column(name = "availabilities")
    @BatchSize(size = 50)
    @AttributeOverrides({
            @AttributeOverride(name = "type", column = @Column(name = "type")),
            @AttributeOverride(name = "sd", column = @Column(name = "sd")),
            @AttributeOverride(name = "hd", column = @Column(name = "hd")),
            @AttributeOverride(name = "fourK", column = @Column(name = "fourK"))
    })
    private final List<Availability> availabilities = new ArrayList<>();

    public QueryResultDB(ImdbId imdbId, String streamingServiceName, boolean flatrate, List<Availability> availabilities, String languages) {
        this.id = null;
        this.imdbId = imdbId;
        this.streamingServiceName = streamingServiceName;
        this.flatrate = flatrate;
        this.languages = languages;
        this.availabilities.addAll(availabilities);
    }

}