package tech.dobler.werstreamt.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tech.dobler.werstreamt.application.dto.OverviewEntryDto;
import tech.dobler.werstreamt.domain.ImdbEntry;
import tech.dobler.werstreamt.services.ImdbCatalog;
import tech.dobler.werstreamt.services.StreamInfoService;

import java.util.Comparator;
import java.util.List;

/**
 * Builds the catalogue overview (the {@code /} landing page and {@code /api/catalog}): every
 * known title with the streaming services it is available on, sorted by name.
 */
@Service
@RequiredArgsConstructor
public class CatalogOverviewService {

    private final ImdbCatalog imdbCatalog;
    private final StreamInfoService streamInfoService;

    public List<OverviewEntryDto> overview() {
        final var entries = imdbCatalog.findAll();
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
