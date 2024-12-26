package tech.dobler.werstreamt.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.dobler.werstreamt.entities.ImdbEntry;
import tech.dobler.werstreamt.services.ImdbEntryRepository;
import tech.dobler.werstreamt.services.StreamInfoService;

import java.util.List;

import static org.springframework.http.ResponseEntity.ok;

@Transactional
@RestController
@RequestMapping("/refresh")
@RequiredArgsConstructor
@Slf4j
public class RefreshController {

    private final ImdbEntryRepository imdbEntryRepository;
    private final StreamInfoService streamInfoService;

    @GetMapping("seen")
    public ResponseEntity<String> refreshSeen() {
        return refreshEntries(imdbEntryRepository.findAllSeen());
    }

    @GetMapping("all")
    public ResponseEntity<String> refreshAll() {
        return refreshEntries(imdbEntryRepository.findAll());
    }

    private ResponseEntity<String> refreshEntries(List<ImdbEntry> entries) {
        log.info("Refreshing {} entries", entries.size());
        final var refreshed = entries.parallelStream()
                .map(entry -> streamInfoService.resolve(entry.imdbId(), true))
                .toList();
        return ok("Refreshed %s".formatted(refreshed.size()));
    }
}
