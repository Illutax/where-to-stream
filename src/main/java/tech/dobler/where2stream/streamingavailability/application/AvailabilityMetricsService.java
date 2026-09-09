package tech.dobler.where2stream.streamingavailability.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tech.dobler.where2stream.shared.platform.time.TimeService;
import tech.dobler.where2stream.streamingavailability.port.in.AvailabilityMetricsPort;
import tech.dobler.where2stream.streamingavailability.port.out.QueryMetaRepository;

/** Streaming Availability's side of the instance metrics: cache size and how much of it is stale. */
@Service
@RequiredArgsConstructor
public class AvailabilityMetricsService implements AvailabilityMetricsPort {

    private final QueryMetaRepository repository;
    private final TimeService timeService;

    @Override
    public AvailabilityMetrics metrics() {
        // Both counts read the same "now", so fresh can never exceed cached through a clock that
        // moved between the two queries -- which would make staleTitles() negative.
        final var now = timeService.now();
        return new AvailabilityMetrics(repository.countCachedTitles(), repository.countFreshTitles(now));
    }
}
