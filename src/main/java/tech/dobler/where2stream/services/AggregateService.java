package tech.dobler.where2stream.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tech.dobler.where2stream.domain.ImdbEntry;
import tech.dobler.where2stream.domain.QueryResult;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/** Derives per-provider offerings from a user's watchlist (resolved against the shared cache). */
@Service
@RequiredArgsConstructor
public class AggregateService {
    private final WatchlistCatalog watchlistCatalog;
    private final StreamInfoService streamInfoService;

    /** Flatrate ("included") + paid offerings of a service, resolved from a single getAll(). */
    public record ServiceContent(List<ImdbEntry> included, List<QueryResult> paid) {
    }

    public List<ImdbEntry> included(String serviceName, UUID userId) {
        return includedFrom(getAll(userId), serviceName, userId);
    }

    public List<QueryResult> paid(String serviceName, UUID userId) {
        return paidFrom(getAll(userId), serviceName);
    }

    /**
     * Resolves the catalogue once and derives both the flatrate and the paid offerings of a
     * service from it (the Amazon page needs both, so this avoids resolving everything twice).
     */
    public ServiceContent contentFor(String serviceName, UUID userId) {
        final var all = getAll(userId);
        return new ServiceContent(includedFrom(all, serviceName, userId), paidFrom(all, serviceName));
    }

    private List<ImdbEntry> includedFrom(List<QueryResult> all, String serviceName, UUID userId) {
        return all.stream()
                .filter(on(serviceName).and(QueryResult::flatrate))
                .map(e -> watchlistCatalog.findByImdb(userId, e.imdbId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Catalogue entry missing for " + e.imdbId())))
                .distinct() // a film is "in the flatrate" once per provider, even with language variants
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

    /** All resolved query results across the user's watchlist (one batched lookup). */
    public List<QueryResult> getAll(UUID userId) {
        final var imdbIds = watchlistCatalog.findAll(userId).stream().map(ImdbEntry::imdbId).toList();
        return streamInfoService.resolveAll(imdbIds).values().stream()
                .flatMap(List::stream)
                .toList();
    }
}
