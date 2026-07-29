package tech.dobler.where2stream.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tech.dobler.where2stream.application.dto.OverviewEntryDto;
import tech.dobler.where2stream.domain.ImdbEntry;
import tech.dobler.where2stream.services.StreamInfoService;
import tech.dobler.where2stream.services.WatchlistCatalog;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Builds the catalogue overview for a user (the {@code /} landing page and {@code /api/catalog}):
 * every title on their watchlist with the streaming services it is available on, sorted by name.
 */
@Service
@RequiredArgsConstructor
public class CatalogOverviewService {

    private final WatchlistCatalog watchlistCatalog;
    private final StreamInfoService streamInfoService;

    public List<OverviewEntryDto> overview(UUID userId) {
        final var entries = watchlistCatalog.findAll(userId);
        // Resolve all entries in a single batch instead of one query per entry (avoids N+1).
        final var resolved = streamInfoService.resolveAll(entries.stream().map(ImdbEntry::imdbId).toList());
        return entries.stream()
                .map(entry -> new OverviewEntryDto(
                        entry.isRated(),
                        entry.name(),
                        entry.imdbId(),
                        entry.year(),
                        entry.added(),
                        StreamInfoService.toAvailableServiceNames(resolved.getOrDefault(entry.imdbId(), List.of()))
                                .orElse(null)))
                .sorted(Comparator.comparing(OverviewEntryDto::name))
                .toList();
    }
}
