package tech.dobler.werstreamt.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamInfoService {
    private final ApiClient apiClient;
    private final FavoriteStreamingServicesRepository favoriteStreamingServicesRepository;
    private final ImdbEntryRepository imdbEntryRepository;

    @EventListener(ApplicationReadyEvent.class)
    void start() {
        final var all = imdbEntryRepository.findAll();
        AtomicInteger counter = new AtomicInteger();
        final var list = all.parallelStream()
                .map(entry -> {
                    log.info("fetching {}", counter.incrementAndGet());
                    return apiClient.query(entry.imdbId());
                })
                .toList();
    }
}
