package tech.dobler.werstreamt.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.dobler.werstreamt.entities.QueryResult;
import tech.dobler.werstreamt.persistence.QueryResultRepository;
import tech.dobler.werstreamt.services.mappers.QueryResultMapper;

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
                .map(QueryResultMapper.INSTANCE::dtoToEntity).toList();
    }

    private List<QueryResult> fetch(String imdbId) {
        log.info("Fetching imdb entries for imdbId {}", imdbId);
        final var queryResults = imdbEntryRepository.findByImdb(imdbId)
                .map(entry -> apiClient.query(entry.imdbId()))
                .orElse(List.of());
        queryResultRepository.saveAll(queryResults.stream().map(QueryResultMapper.INSTANCE::entityToDto).toList());
        return queryResults;
    }
}
