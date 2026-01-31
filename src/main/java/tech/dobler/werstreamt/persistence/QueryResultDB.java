package tech.dobler.werstreamt.persistence;

import jakarta.persistence.*;
import lombok.*;
import tech.dobler.werstreamt.entities.Availability;

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
    @Column(name = "imdbId")
    private final String imdbId;
    @Column(name = "title")
    private final String streamingServiceName;
    @Column(name = "flatrate")
    private final boolean flatrate;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "query_result_availablilities", joinColumns = @JoinColumn(name = "imdb_id"))
    @Column(name = "availabilities")
    @AttributeOverrides({
            @AttributeOverride(name = "type", column = @Column(name = "type")),
            @AttributeOverride(name = "sd", column = @Column(name = "sd")),
            @AttributeOverride(name = "hd", column = @Column(name = "hd")),
            @AttributeOverride(name = "fourK", column = @Column(name = "fourK"))
    })
    private final List<Availability> availabilities = new ArrayList<>();

    public QueryResultDB(String imdbId, String streamingServiceName, boolean flatrate, List<Availability> availabilities) {
        this.id = null;
        this.imdbId = imdbId;
        this.streamingServiceName = streamingServiceName;
        this.flatrate = flatrate;
        this.availabilities.addAll(availabilities);
    }

}