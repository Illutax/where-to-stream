package tech.dobler.werstreamt.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.dobler.werstreamt.services.PreCacheService;

import static org.springframework.http.ResponseEntity.ok;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PreCacheController {
    private final PreCacheService preCacheService;

    @GetMapping("/pre-cache")
    public ResponseEntity<String> cache() {
        final var count = preCacheService.cacheAll();
        return ok("cached " + count + " imdb entries");
    }

    @GetMapping("/check-pre-cache")
    ResponseEntity<String> checkCache() {
        final var uncached = preCacheService.findUncached();
        return ok("%d uncached imdb entries".formatted(uncached.size()));
    }
}
