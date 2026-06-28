package tech.dobler.werstreamt.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tech.dobler.werstreamt.domain.ImdbEntry;
import tech.dobler.werstreamt.domain.QueryResult;

import java.util.List;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
public class AggregateService {
    private final ImdbCatalog imdbCatalog;
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

    private List<ImdbEntry> includedFrom(List<QueryResult> all, String serviceName) {
        return all.stream()
                .filter(on(serviceName).and(QueryResult::flatrate))
                .map(e -> imdbCatalog.findByImdb(e.imdbId()).get())
                .toList();
    }

    private List<QueryResult> paidFrom(List<QueryResult> all, String serviceName) {
        return all.stream()
                .filter(on(serviceName).and(Predicate.not(QueryResult::flatrate)))
                .toList();
    }

    private static Predicate<QueryResult> on(String serviceName) {
        return result -> serviceName.equals(result.streamingServiceName());
    }

    /** All resolved query results across the catalogue (one batched lookup). */
    public List<QueryResult> getAll() {
        final var imdbIds = imdbCatalog.findAll().stream().map(ImdbEntry::imdbId).toList();
        return streamInfoService.resolveAll(imdbIds).values().stream()
                .flatMap(List::stream)
                .toList();
    }
}
