package tech.dobler.werstreamt.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import tech.dobler.werstreamt.application.dto.RefreshResultDto;
import tech.dobler.werstreamt.domain.ImdbId;
import tech.dobler.werstreamt.persistence.WatchlistEntryRepository;
import tech.dobler.werstreamt.services.StreamInfoService;

import java.util.List;

/**
 * Force-refreshes cached stream availability across all users' watchlists (global, ADMIN), for all
 * titles or only the seen ones.
 *
 * <p>Deliberately not {@code @Transactional}: the refresh fans out over a {@code parallelStream}
 * and each worker calls the proxied {@link StreamInfoService#resolve(String, boolean)}, which
 * opens its own transaction per thread (see the NOTE in {@code StreamInfoService}).
 *
 * <p>ADMIN-only (enforced both by the {@code POST /api/refresh} URL rule in {@code SecurityConfig}
 * and {@link PreAuthorize} here as defense in depth, mirroring {@code UserAdminService}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class RefreshService {

    private final WatchlistEntryRepository watchlistEntryRepository;
    private final StreamInfoService streamInfoService;

    public RefreshResultDto refreshSeen() {
        return refresh(watchlistEntryRepository.findDistinctImdbIdsRated());
    }

    public RefreshResultDto refreshAll() {
        return refresh(watchlistEntryRepository.findDistinctImdbIds());
    }

    private RefreshResultDto refresh(List<ImdbId> imdbIds) {
        log.info("Refreshing {} titles", imdbIds.size());
        final var refreshed = imdbIds.parallelStream()
                .map(imdbId -> streamInfoService.resolve(imdbId, true))
                .toList();
        return new RefreshResultDto(refreshed.size());
    }
}
