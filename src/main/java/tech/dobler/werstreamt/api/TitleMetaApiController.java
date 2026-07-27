package tech.dobler.werstreamt.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.dobler.werstreamt.application.TitleInfoService;
import tech.dobler.werstreamt.application.dto.MetaDto;
import tech.dobler.werstreamt.domain.ImdbId;

import java.time.Duration;

/**
 * Serves a title's row metadata for the FSK badge + German-title toggle:
 * {@code {"rating":{"system":"FSK","label":"16"},"germanTitle":"Oben"}} (either field may be null).
 * Backed by the shared IMDb metadata cache — the same one fetch as the poster. 404 only when the
 * title is unknown / the fetch failed. Auth is inherited (like the poster endpoints).
 */
@RestController
@RequestMapping("/api/titles")
@RequiredArgsConstructor
public class TitleMetaApiController {

    private final TitleInfoService titleInfoService;

    @GetMapping("/{imdbId}/meta")
    public ResponseEntity<MetaDto> meta(@PathVariable ImdbId imdbId) {
        return titleInfoService.metaFor(imdbId)
                .map(dto -> ResponseEntity.ok()
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                        .body(dto))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
