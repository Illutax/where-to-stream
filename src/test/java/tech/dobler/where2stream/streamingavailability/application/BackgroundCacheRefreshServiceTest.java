package tech.dobler.where2stream.streamingavailability.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.shared.platform.concurrency.RefreshInFlightTracker;
import tech.dobler.where2stream.shared.platform.time.TimeService;
import tech.dobler.where2stream.streamingavailability.domain.QueryMeta;
import tech.dobler.where2stream.streamingavailability.port.out.QueryMetaRepository;
import tech.dobler.where2stream.watchlist.port.in.WatchlistCatalogPort;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackgroundCacheRefreshServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private WatchlistCatalogPort watchlistCatalogPort;
    @Mock
    private QueryMetaRepository queryMetaRepository;
    @Mock
    private RefreshInFlightTracker tracker;
    @Mock
    private StreamInfoService streamInfoService;
    @Mock
    private TimeService timeService;
    @InjectMocks
    private BackgroundCacheRefreshService service;

    private static ImdbId id(String imdbId) {
        return ImdbId.of(imdbId);
    }

    private static QueryMeta freshMeta(String imdbId, Instant dueForRefreshAt) {
        return new QueryMeta(UUID.randomUUID(), id(imdbId), NOW, dueForRefreshAt, false, List.of());
    }

    private static QueryMeta invalidatedMeta(String imdbId) {
        return new QueryMeta(UUID.randomUUID(), id(imdbId), NOW, null, true, List.of());
    }

    @BeforeEach
    void setUp() {
        lenient().when(timeService.now()).thenReturn(NOW);
    }

    @Test
    void nothingDueQueuesNothing() {
        when(watchlistCatalogPort.allDistinctImdbIds()).thenReturn(List.of(id("tt1")));
        when(queryMetaRepository.findByImdbIdIn(List.of(id("tt1"))))
                .thenReturn(List.of(freshMeta("tt1", NOW.plus(10, ChronoUnit.DAYS))));

        assertThat(service.refreshDueEntries()).isZero();
        verifyNoInteractions(streamInfoService);
    }

    @Test
    void noWatchlistTitlesAtAllIsANoopWithoutQueryingTheRepository() {
        when(watchlistCatalogPort.allDistinctImdbIds()).thenReturn(List.of());

        assertThat(service.refreshDueEntries()).isZero();
        verifyNoInteractions(queryMetaRepository, streamInfoService);
    }

    @Test
    void queuesTitlesPastTheirDueDateOrInvalidatedButSkipsOnesAlreadyInFlight() {
        final var imdbIds = List.of(id("tt1"), id("tt2"), id("tt3"));
        when(watchlistCatalogPort.allDistinctImdbIds()).thenReturn(imdbIds);
        when(queryMetaRepository.findByImdbIdIn(imdbIds)).thenReturn(List.of(
                freshMeta("tt1", NOW.minus(1, ChronoUnit.DAYS)), // due
                invalidatedMeta("tt2"),                          // due
                freshMeta("tt3", NOW.plus(1, ChronoUnit.DAYS)))); // not due
        when(tracker.tryStart(id("tt1"))).thenReturn(true);
        when(tracker.tryStart(id("tt2"))).thenReturn(false); // already in flight

        final int queued = service.refreshDueEntries();

        assertThat(queued).isEqualTo(1);
        verify(streamInfoService).refreshInBackground(id("tt1"));
        verify(streamInfoService, never()).refreshInBackground(id("tt2"));
        verify(streamInfoService, never()).refreshInBackground(id("tt3"));
    }

    @Test
    void onlyTheLatestRowPerImdbIdDecidesWhetherATitleIsDue() {
        // Older row is invalidated, but the newest row for the same title is fresh and not due.
        when(watchlistCatalogPort.allDistinctImdbIds()).thenReturn(List.of(id("tt1")));
        when(queryMetaRepository.findByImdbIdIn(List.of(id("tt1")))).thenReturn(List.of(
                new QueryMeta(UUID.randomUUID(), id("tt1"), NOW.minus(2, ChronoUnit.DAYS), null, true, List.of()),
                freshMeta("tt1", NOW.plus(10, ChronoUnit.DAYS))));

        assertThat(service.refreshDueEntries()).isZero();
        verify(streamInfoService, never()).refreshInBackground(any());
    }
}
