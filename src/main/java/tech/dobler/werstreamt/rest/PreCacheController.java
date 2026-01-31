package tech.dobler.werstreamt.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.dobler.werstreamt.entities.ImdbEntry;
import tech.dobler.werstreamt.persistence.QueryMeta;
import tech.dobler.werstreamt.persistence.QueryMetaRepository;
import tech.dobler.werstreamt.services.ImdbEntryRepository;
import tech.dobler.werstreamt.services.StreamInfoService;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.springframework.http.ResponseEntity.ok;

@Slf4j
@RestController
@RequiredArgsConstructor
@Transactional
public class PreCacheController {
    private final StreamInfoService streamInfoService;
    private final ImdbEntryRepository imdbEntryRepository;
    private final QueryMetaRepository queryMetaRepository;

    @GetMapping("/pre-cache")
    public ResponseEntity<String> cache() {
        final var all = imdbEntryRepository.findAll();
        final var counter = new AtomicInteger(0);
        all.parallelStream()
                .forEach(e -> {
                    streamInfoService.resolve(e.imdbId());
                    if (counter.incrementAndGet() % 10 == 0) {
                        log.info("got {} imdb entries", counter.get());
                    }
                });
        return ok("cached " + counter.get() + " imdb entries");
    }

    @GetMapping("/check-pre-cache")
    ResponseEntity<String> checkCache() {
        record IsPresent(ImdbEntry entry, Optional<QueryMeta> queryMeta) {}

        final var all = imdbEntryRepository.findAll();
        final var list = all.parallelStream()
                .map(e -> new IsPresent(e, queryMetaRepository.findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc(e.imdbId())))
                .filter(e -> e.queryMeta.isEmpty())
                .toList();

        list.stream()
                .map(IsPresent::toString)
                .forEach(log::warn);
        return ok("");

    }


}
