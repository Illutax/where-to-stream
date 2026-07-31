package tech.dobler.where2stream.streamingavailability.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tech.dobler.where2stream.watchlist.domain.ImdbEntry;
import tech.dobler.where2stream.streamingavailability.domain.QueryResult;
import tech.dobler.where2stream.watchlist.port.in.WatchlistCatalogPort;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Derives per-provider offerings from a user's watchlist (resolved against the shared cache). */
@Service
@RequiredArgsConstructor
public class AggregateService {
    private final WatchlistCatalogPort watchlistCatalogPort;
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
     * Resolves the catalogue once and derives both the flatrate and the paid offerings of a service
     * from it (the Amazon page needs both, so this avoids resolving everything twice).
     */
    public ServiceContent contentFor(String serviceName, UUID userId) {
        final var all = getAll(userId);
        return new ServiceContent(includedFrom(all, serviceName, userId), paidFrom(all, serviceName));
    }

    private List<ImdbEntry> includedFrom(List<QueryResult> all, String serviceName, UUID userId) {
        return all.stream()
                .filter(on(serviceName).and(QueryResult::flatrate))
                .map(e -> watchlistCatalogPort.findByImdb(userId, e.imdbId())
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
        return resolveAll(userId).values().stream()
                .flatMap(entry -> entry.results().stream())
                .toList();
    }

    /**
     * Whether any of the user's watchlist titles are currently served from stale
     * (invalidated/expired) cache data while a background refresh is under way (ADR-0016).
     * Resolves the catalogue again rather than sharing state with {@link #getAll}/{@link #included}/
     * {@link #paid}/{@link #contentFor} — those calls hit the same already-cached rows, so the
     * extra lookup costs one more indexed batch query, not a re-scrape.
     */
    public boolean hasStaleEntries(UUID userId) {
        return resolveAll(userId).values().stream().anyMatch(ResolvedEntry::stale);
    }

    private Map<ImdbId, ResolvedEntry> resolveAll(UUID userId) {
        final var imdbIds = watchlistCatalogPort.findAll(userId).stream().map(ImdbEntry::imdbId).toList();
        return streamInfoService.resolveAll(imdbIds);
    }
}
