package tech.dobler.where2stream.streamingavailability.adapter.in.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tech.dobler.where2stream.streamingavailability.application.SearchService;
import tech.dobler.where2stream.shared.domain.ImdbId;
import tech.dobler.where2stream.streamingavailability.domain.QueryResult;

import java.util.List;

/**
 * Resolve stream availability for a single title by IMDb id ({@code ?imdbId=tt…}) from the
 * shared cache.
 * 404 when nothing is available.
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchApiController {

    private final SearchService searchService;

    @GetMapping
    public List<QueryResult> search(@RequestParam("imdbId") ImdbId imdbId) {
        return searchService.resolveByImdbId(imdbId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No availability found"));
    }
}
