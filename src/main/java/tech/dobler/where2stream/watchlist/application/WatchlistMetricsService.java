package tech.dobler.where2stream.watchlist.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tech.dobler.where2stream.watchlist.port.in.WatchlistMetricsPort;
import tech.dobler.where2stream.watchlist.port.out.WatchlistEntryRepository;

/** Watchlist's side of the instance metrics: how many titles and how many rows. */
@Service
@RequiredArgsConstructor
public class WatchlistMetricsService implements WatchlistMetricsPort {

    private final WatchlistEntryRepository repository;

    @Override
    public WatchlistMetrics metrics() {
        return new WatchlistMetrics(repository.countDistinctTitles(), repository.count());
    }

    @Override
    public long countDistinctTitles() {
        return repository.countDistinctTitles();
    }
}
