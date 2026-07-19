package tech.dobler.werstreamt.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.dobler.werstreamt.application.SearchService;
import tech.dobler.werstreamt.domain.QueryResult;

import java.util.List;

import static org.springframework.http.ResponseEntity.ok;

/**
 * Legacy single-title lookup endpoints. Kept for backwards compatibility; new clients should
 * use {@code /api/search}. All resolution logic lives in {@link SearchService}.
 */
@RestController
@RequiredArgsConstructor
public class QueryController {
    private final SearchService searchService;

    @GetMapping("/query")
    public ResponseEntity<?> query(@RequestParam(name = "id") int id)
    {
        return searchService.liveQueryByCatalogId(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam(name = "imdbId", required = false) String imdbId,
                                    @RequestParam(name = "id", required = false) Integer id)
    {
        final var result = id == null
                ? searchService.resolveByImdbId(imdbId)
                : searchService.resolveByCatalogId(id);
        return result
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
