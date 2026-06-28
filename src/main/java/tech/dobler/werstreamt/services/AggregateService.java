package tech.dobler.werstreamt.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tech.dobler.werstreamt.entities.ImdbEntry;
import tech.dobler.werstreamt.entities.QueryResult;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AggregateService {
    private final ImdbEntryRepository imdbEntryRepository;
    private final StreamInfoService streamInfoService;

    /** Flatrate ("included") + paid offerings of a service, resolved from a single getAll(). */
    public record ServiceContent(List<ImdbEntry> included, List<QueryResult> paid) {
    }

    public List<ImdbEntry> included(String serviceName) {
        return includedFrom(getAll(), serviceName);
    }

    public List<QueryResult> paid(String serviceName) {
        return paidFrom(getAll(), serviceName);
    }

    /**
     * Resolves the catalogue once and derives both the flatrate and the paid offerings of a
     * service from it (the Amazon page needs both, so this avoids resolving everything twice).
     */
    public ServiceContent contentFor(String serviceName) {
        final var all = getAll();
        return new ServiceContent(includedFrom(all, serviceName), paidFrom(all, serviceName));
    }

    private List<ImdbEntry> includedFrom(List<List<QueryResult>> queryResults, String serviceName) {
        return queryResults.stream()
                .flatMap(Collection::stream)
                .filter(k -> serviceName.equals(k.streamingServiceName()) && k.flatrate())
                .map(e -> imdbEntryRepository.findByImdb(e.imdbId()).get())
                .toList();
    }

    private List<QueryResult> paidFrom(List<List<QueryResult>> queryResults, String serviceName) {
        return queryResults.stream()
                .flatMap(Collection::stream)
                .filter(k -> serviceName.equals(k.streamingServiceName()) && !k.flatrate())
                .toList();
    }

    public List<List<QueryResult>> getAll() {
        final var allImdbEntries = imdbEntryRepository.findAll();
        return allImdbEntries.stream()
                .map(e -> streamInfoService.resolve(e.imdbId()))
                .toList();
    }
}
