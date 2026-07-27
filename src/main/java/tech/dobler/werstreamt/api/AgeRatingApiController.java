package tech.dobler.werstreamt.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.dobler.werstreamt.application.AgeRatingService;
import tech.dobler.werstreamt.domain.AgeRating;
import tech.dobler.werstreamt.domain.ImdbId;

import java.time.Duration;

/**
 * Serves a title's age rating for the FSK badge: {@code {"system":"FSK","label":"16"}} (or a
 * fallback certificate) — 404 when the title has no rating. Backed by the shared IMDb metadata
 * cache, so it costs the same one fetch as the poster. Auth is inherited (like the poster endpoints).
 */
@RestController
@RequestMapping("/api/titles")
@RequiredArgsConstructor
public class AgeRatingApiController {

    private final AgeRatingService ageRatingService;

    @GetMapping("/{imdbId}/rating")
    public ResponseEntity<AgeRating> rating(@PathVariable ImdbId imdbId) {
        return ageRatingService.ratingFor(imdbId)
                .map(rating -> ResponseEntity.ok()
                        // Ratings are stable; let the browser cache them (explicit Cache-Control wins
                        // over Spring Security's default no-store).
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                        .body(rating))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
