package tech.dobler.werstreamt.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.dobler.werstreamt.entities.QueryResult;
import tech.dobler.werstreamt.persistence.QueryMeta;
import tech.dobler.werstreamt.persistence.QueryMetaRepository;
import tech.dobler.werstreamt.services.mappers.QueryResultMapper;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamInfoService {
    private final ApiClient apiClient;
    private final ImdbEntryRepository imdbEntryRepository;
    private final QueryMetaRepository queryMetaRepository;

    public List<QueryResult> resolve(String imdbId) {
        final var result = queryMetaRepository.findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc(imdbId);
        return result.map(queryMeta -> queryMeta.getQueries()
                        .stream()
                        .map(QueryResultMapper.INSTANCE::dtoToEntity)
                        .toList())
                .orElseGet(() -> fetch(imdbId));
    }

    private List<QueryResult> fetch(String imdbId) {
        log.info("Fetching imdb entries for imdbId {}", imdbId);
        final var queryResults = imdbEntryRepository.findByImdb(imdbId)
                .map(entry -> apiClient.query(entry.imdbId()))
                .orElse(List.of());
        final var list = queryResults.stream().map(QueryResultMapper.INSTANCE::entityToDto).toList();
        queryMetaRepository.save(QueryMeta.of(imdbId, Instant.now(), list));
        return queryResults;
    }
}
