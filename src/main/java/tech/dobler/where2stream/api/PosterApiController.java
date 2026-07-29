package tech.dobler.where2stream.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.dobler.where2stream.application.PosterService;
import tech.dobler.where2stream.application.PosterService.Poster;
import tech.dobler.where2stream.domain.ImdbId;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Serves the cached poster images for a title: the small thumbnail (row) and the larger hover
 * image. Bytes are served with long, public cache headers + an ETag so each browser fetches a
 * poster once. 404 when there is no poster (or the feature is off) — the SPA hides the image.
 */
@RestController
@RequestMapping("/api/titles")
@RequiredArgsConstructor
public class PosterApiController {

    private final PosterService posterService;

    @GetMapping("/{imdbId}/poster")
    public ResponseEntity<byte[]> thumbnail(@PathVariable ImdbId imdbId) {
        return respond(posterService.thumb(imdbId));
    }

    @GetMapping("/{imdbId}/poster/full")
    public ResponseEntity<byte[]> full(@PathVariable ImdbId imdbId) {
        return respond(posterService.full(imdbId));
    }

    private ResponseEntity<byte[]> respond(Optional<Poster> poster) {
        return poster
                .map(p -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(p.contentType()))
                        // Explicit Cache-Control wins over Spring Security's default no-store, so
                        // the browser actually caches the image.
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                        .eTag(Integer.toHexString(Arrays.hashCode(p.bytes())))
                        .body(p.bytes()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
