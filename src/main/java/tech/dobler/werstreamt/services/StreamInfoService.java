package tech.dobler.werstreamt.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import tech.dobler.werstreamt.entities.QueryResult;
import tech.dobler.werstreamt.persistence.QueryResultDB;
import tech.dobler.werstreamt.persistence.QueryResultRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamInfoService {
    private final ApiClient apiClient;
    private final ImdbEntryRepository imdbEntryRepository;
    private final QueryResultRepository queryResultRepository;

    public List<QueryResult> resolve(String imdbId) {
        final var result = queryResultRepository.findByImdbId(imdbId);
        if (result.isEmpty()) {
            return fetch(imdbId);
        }
        return result.stream()
                .map(this::map).toList();
    }

    private QueryResult map(QueryResultDB db) {
        return new QueryResult(db.getImdbId(), db.getTitle(), db.isFlatrate(), db.getAvailabilities());
    }

    private List<QueryResult> fetch(String imdbId) {
        log.info("Fetching imdb entries for imdbId {}", imdbId);
        final var queryResults = imdbEntryRepository.findByImdb(imdbId)
                .map(entry -> apiClient.query(entry.imdbId()))
                .orElse(List.of());
        queryResultRepository.saveAll(queryResults.stream().map(this::map2db).toList());
        return queryResults;
    }

    private QueryResultDB map2db(QueryResult q) {
        return new QueryResultDB(q.imdbId(), q.title(), q.flatrate(), q.availabilities());
    }

    @EventListener(ApplicationReadyEvent.class)
    void resolve() {
        final var prices = resolve("tt0292644");
    }
}
