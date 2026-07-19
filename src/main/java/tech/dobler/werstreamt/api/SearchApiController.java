package tech.dobler.werstreamt.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tech.dobler.werstreamt.application.SearchService;
import tech.dobler.werstreamt.domain.QueryResult;

import java.util.List;

/**
 * Resolve stream availability for a single title, by IMDb id ({@code ?imdbId=tt…}) or by
 * catalogue id ({@code ?id=…}). 404 when the title is unknown or nothing is available.
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchApiController {

    private final SearchService searchService;

    @GetMapping
    public List<QueryResult> search(@RequestParam(name = "imdbId", required = false) String imdbId,
                                    @RequestParam(name = "id", required = false) Integer id) {
        if (imdbId == null && id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide either imdbId or id");
        }
        final var result = id == null
                ? searchService.resolveByImdbId(imdbId)
                : searchService.resolveByCatalogId(id);
        return result.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No availability found"));
    }
}
