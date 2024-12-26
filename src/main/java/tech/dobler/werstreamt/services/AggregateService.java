package tech.dobler.werstreamt.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tech.dobler.werstreamt.entities.ImdbEntry;
import tech.dobler.werstreamt.entities.QueryResult;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
public class AggregateService {
    private final ImdbEntryRepository imdbEntryRepository;
    private final StreamInfoService streamInfoService;

    public List<ImdbEntry> included(String serviceName){
        final var queryResults = getAll();

        return queryResults.stream()
                .map(e -> e.stream().filter(k -> serviceName.equals(k.streamingServiceName()) && k.flatrate()).toList())
                .filter(Predicate.not(List::isEmpty))
                .flatMap(Collection::stream)
                .map(e -> imdbEntryRepository.findByImdb(e.imdbId()).get())
                .toList();
    }

    public List<QueryResult> paid(String serviceName){
        final var queryResults = getAll();

        return queryResults.stream()
                .map(e -> e.stream().filter(k -> serviceName.equals(k.streamingServiceName()) && !k.flatrate()).toList())
                .filter(Predicate.not(List::isEmpty))
                .flatMap(Collection::stream)
                .toList();
    }

    public List<List<QueryResult>> getAll() {
        final var allImdbEntries = imdbEntryRepository.findAll();
        return allImdbEntries.stream()
                .map(e -> streamInfoService.resolve(e.imdbId()))
                .toList();
    }
}
