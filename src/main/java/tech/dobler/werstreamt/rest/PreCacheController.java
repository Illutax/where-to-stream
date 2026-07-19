package tech.dobler.werstreamt.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.dobler.werstreamt.application.CacheManagementService;

import static org.springframework.http.ResponseEntity.ok;

/**
 * Legacy maintenance endpoints (unauthenticated GETs with side effects — see TODOs.md TODO-5).
 * Kept for backwards compatibility; new clients should use the {@code /api/**} endpoints.
 */
@RestController
@RequiredArgsConstructor
public class PreCacheController {
    private final CacheManagementService cacheManagementService;

    @GetMapping("/pre-cache")
    public ResponseEntity<String> cache() {
        final var count = cacheManagementService.cacheAll().cached();
        return ok("cached " + count + " imdb entries");
    }

    @GetMapping("/check-pre-cache")
    ResponseEntity<String> checkCache() {
        final var uncached = cacheManagementService.uncachedCount().uncached();
        return ok("%d uncached imdb entries".formatted(uncached));
    }
}
