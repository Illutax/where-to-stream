package tech.dobler.werstreamt.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.dobler.werstreamt.application.dto.RefreshResultDto;
import tech.dobler.werstreamt.domain.ImdbEntry;
import tech.dobler.werstreamt.services.ImdbCatalog;
import tech.dobler.werstreamt.services.StreamInfoService;

import java.util.List;

/**
 * Force-refreshes cached stream availability, for all titles or only the seen ones.
 *
 * <p>Deliberately not {@code @Transactional}: the refresh fans out over a {@code parallelStream}
 * and each worker calls the proxied {@link StreamInfoService#resolve(String, boolean)}, which
 * opens its own transaction per thread. A method-level transaction here would not span the
 * {@code ForkJoinPool} workers (see the NOTE in {@code StreamInfoService}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshService {

    private final ImdbCatalog imdbCatalog;
    private final StreamInfoService streamInfoService;

    public RefreshResultDto refreshSeen() {
        return refreshEntries(imdbCatalog.findAllSeen());
    }

    public RefreshResultDto refreshAll() {
        return refreshEntries(imdbCatalog.findAll());
    }

    private RefreshResultDto refreshEntries(List<ImdbEntry> entries) {
        log.info("Refreshing {} entries", entries.size());
        final var refreshed = entries.parallelStream()
                .map(entry -> streamInfoService.resolve(entry.imdbId(), true))
                .toList();
        return new RefreshResultDto(refreshed.size());
    }
}
