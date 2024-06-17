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

import java.util.List;
import java.util.function.Predicate;

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
        final var queryResults = allImdbEntries.stream()
                .map(e -> streamInfoService.resolve(e.imdbId()))
                .toList();
        final var resultsOnMyServices = queryResults.stream()
                .map(e -> e.stream().filter(k -> favoriteServices.contains(k.streamingServiceName())).toList())
                .filter(Predicate.not(List::isEmpty))
                .toList();

        final var resultsOnMyServicesWithFlatrate = queryResults.stream()
                .map(e -> e.stream().filter(k -> favoriteServices.contains(k.streamingServiceName()) && k.flatrate()).toList())
                .filter(Predicate.not(List::isEmpty))
                .toList();
        // TODO: get a good overview of <What> is available <Where> and <How>

        return ok("k");
    }
}
