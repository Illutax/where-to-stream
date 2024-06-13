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

import java.util.List;

import static org.springframework.http.ResponseEntity.ok;

@Slf4j
@RestController
@RequiredArgsConstructor
public class QueryController {
    private final ImdbEntryRepository entryRepository;
    private final ApiClient apiClient;

    @GetMapping("/query")
    public ResponseEntity<List<QueryResult>> get(@RequestParam(name = "id") int id)
    {
        final var maybeEntry = entryRepository.findById(id);
        final var name = maybeEntry.map(ImdbEntry::name);
        log.info("Requesting {} with name {}", id, name.orElse("Not found"));
        if (maybeEntry.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        final var entry = maybeEntry.get();

        final var queryResult = apiClient.query(entry.imdbId());

        return ok(queryResult);
    }
}
