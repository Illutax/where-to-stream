package tech.dobler.werstreamt.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.dobler.werstreamt.entities.ImdbEntry;
import tech.dobler.werstreamt.entities.QueryResult;
import tech.dobler.werstreamt.services.ApiClient;
import tech.dobler.werstreamt.services.ImdbEntryRepository;
import tech.dobler.werstreamt.services.StreamInfoService;

import java.util.List;
import java.util.Optional;

import static org.springframework.http.ResponseEntity.ok;

@Slf4j
@RestController
@RequiredArgsConstructor
public class QueryController {
    private final ImdbEntryRepository entryRepository;
    private final ApiClient apiClient;
    private final StreamInfoService streamInfoService;

    @GetMapping("/query")
    public ResponseEntity<?> query(@RequestParam(name = "id") int id)
    {
        final var maybeEntry = entryRepository.findById(id);
        log(id, maybeEntry);
        return maybeEntry
                .map(imdbEntry -> ok(apiClient.query(imdbEntry.imdbId())))
                .orElse(notFound());

    }

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam(name = "imdbId", required = false) String imdbId, @RequestParam(name = "id", required = false) Integer id)
    {
        return id == null
                ? searchByImdbId(imdbId)
                : searchById(id);
    }

    private ResponseEntity<List<QueryResult>> searchById(int id)
    {
        final var maybeEntry = entryRepository.findById(id);
        log(id, maybeEntry);
        return maybeEntry
                .map(e -> searchByImdbId(e.imdbId()))
                .orElse(notFound());
    }

    private ResponseEntity<List<QueryResult>> searchByImdbId(String imdbId)
    {
        final var searchResult = streamInfoService.resolve(imdbId);
        if (!searchResult.isEmpty()) {
            return ok(searchResult);
        }
        return notFound();

    }

    private static void log(int id, Optional<ImdbEntry> maybeEntry) {
        final var name = maybeEntry.map(ImdbEntry::name);
        log.info("Requesting {} with name {}", id, name.orElse("<Not found>"));
    }

    private static ResponseEntity<List<QueryResult>> notFound() {
        return ResponseEntity.notFound().build();
    }
}
