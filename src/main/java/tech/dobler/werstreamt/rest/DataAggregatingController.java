package tech.dobler.werstreamt.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.dobler.werstreamt.services.FavoriteStreamingServicesRepository;
import tech.dobler.werstreamt.services.ImdbEntryRepository;
import tech.dobler.werstreamt.services.StreamInfoService;

import static org.springframework.http.ResponseEntity.ok;

@Slf4j
@RestController
@RequiredArgsConstructor
public class DataAggregatingController {
    private final ImdbEntryRepository imdbEntryRepository;
    private final StreamInfoService streamInfoService;
    private final FavoriteStreamingServicesRepository favoriteStreamingServicesRepository;

    @Transactional
    @GetMapping("/data/q")
    public ResponseEntity<String> q(){
        final var allImdbEntries = imdbEntryRepository.findAll();
        final var favoriteServices = favoriteStreamingServicesRepository.getFavoriteServices();
        final var queryResults = allImdbEntries.parallelStream()
                .map(e -> streamInfoService.resolve(e.imdbId()))
                .toList();
        final var queriesWithMyServices = queryResults.stream()
                .filter(e -> e.stream().anyMatch(k -> favoriteServices.contains(k.title())))
                .toList();

        return ok("k");
    }
}
