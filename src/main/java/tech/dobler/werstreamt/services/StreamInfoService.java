package tech.dobler.werstreamt.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tech.dobler.werstreamt.entities.QueryResult;
import tech.dobler.werstreamt.persistence.QueryMeta;
import tech.dobler.werstreamt.persistence.QueryMetaRepository;
import tech.dobler.werstreamt.services.mappers.QueryResultMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamInfoService {
    private final WerStreamtEsApiClient werStreamtEsApiClient;
    private final ImdbEntryRepository imdbEntryRepository;
    private final QueryMetaRepository queryMetaRepository;

    @Value("${wer-streamt.invalidate.after-days:28}")
    private int duration;

    public List<QueryResult> resolve(String imdbId, boolean forceRefresh) {
        final var result = queryMetaRepository.findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc(imdbId);
        final var daysSeconds = TimeUnit.DAYS.toSeconds(duration);
        return result
                .filter(queryMeta -> {
                    if (forceRefresh) return true;
                    Instant threshold = queryMeta.getCreationTime()
                            .plusSeconds(daysSeconds);
                    Instant now = Instant.now();
                    boolean passedThreshold = threshold.isBefore(now);
                    if (passedThreshold) {
                        log.warn("Entry with id {} passed threshold {} > {}", queryMeta.getImdbId(), threshold, now);
                    }
                    return !passedThreshold;
                })
                .map(queryMeta -> queryMeta.getQueries()
                        .stream()
                        .map(QueryResultMapper.INSTANCE::dtoToEntity)
                        .toList())
                .orElseGet(() -> fetch(imdbId));
    }

    public List<QueryResult> resolve(String imdbId) {
        return resolve(imdbId, false);
    }

    public Optional<String> listAllAvailableServiceNames(String imdbId) {
        final var queryResults = resolve(imdbId);
        if (queryResults.isEmpty()) return Optional.empty();
        return Optional.of(queryResults.stream()
                .map(QueryResult::streamingServiceName)
                .collect(Collectors.joining(", ")));
    }

    private List<QueryResult> fetch(String imdbId) {
        log.info("Fetching imdb entries for imdbId {}", imdbId);
        final var queryResults = imdbEntryRepository.findByImdb(imdbId)
                .map(entry -> werStreamtEsApiClient.query(entry.imdbId()))
                .orElse(List.of());
        final var list = queryResults.stream().map(QueryResultMapper.INSTANCE::entityToDto).toList();
        queryMetaRepository.save(QueryMeta.of(imdbId, Instant.now(), list));
        return queryResults;
    }
}
