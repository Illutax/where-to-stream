package tech.dobler.where2stream.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.where2stream.application.dto.FlatrateEntryDto;
import tech.dobler.where2stream.application.dto.PaidEntryDto;
import tech.dobler.where2stream.domain.Availability;
import tech.dobler.where2stream.watchlist.domain.ImdbEntry;
import tech.dobler.where2stream.shared.domain.ImdbId;
import tech.dobler.where2stream.shared.domain.ReleaseYear;
import tech.dobler.where2stream.watchlist.domain.WatchlistDate;
import tech.dobler.where2stream.domain.QueryResult;
import tech.dobler.where2stream.services.AggregateService;
import tech.dobler.where2stream.watchlist.port.in.WatchlistCatalogPort;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderPageServiceTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private AggregateService aggregateService;
    @Mock
    private WatchlistCatalogPort watchlistCatalogPort;
    @InjectMocks
    private ProviderPageService service;

    private static ImdbId id(String imdbId) {
        return ImdbId.of(imdbId);
    }

    private static ImdbEntry entry(String imdbId, String name, String added, int year) {
        return new ImdbEntry(name, URI.create("https://www.imdb.com/title/" + imdbId + "/"),
                WatchlistDate.of(added), false, ReleaseYear.of(year), id(imdbId));
    }

    private static QueryResult paid(String imdbId, String serviceName) {
        return new QueryResult(id(imdbId), serviceName, false, List.<Availability>of(), "Deutsch");
    }

    @Test
    void amazonPageHasIncludedSortedByAddedAndPaid() {
        final var later = entry("tt1", "Later", "2021-05-05", 2021);
        final var earlier = entry("tt2", "Earlier", "2020-01-01", 2020);
        final var paidEntry = entry("tt3", "Paid", "2022-02-02", 2022);
        when(aggregateService.contentFor("Prime Video", USER)).thenReturn(
                new AggregateService.ServiceContent(List.of(later, earlier), List.of(paid("tt3", "Prime Video"))));
        when(watchlistCatalogPort.findByImdb(USER, id("tt3"))).thenReturn(Optional.of(paidEntry));

        final var page = service.pageFor(StreamingProvider.AMAZON, USER);

        assertThat(page.provider()).isEqualTo("amazon");
        assertThat(page.included()).extracting(FlatrateEntryDto::added)
                .containsExactly(WatchlistDate.of("2020-01-01"), WatchlistDate.of("2021-05-05"));
        assertThat(page.paid()).extracting(PaidEntryDto::name).containsExactly("Paid");
    }

    @Test
    void flatrateOnlyProviderHasNoPaidAndNeverQueriesPaid() {
        when(aggregateService.included("Disney+", USER)).thenReturn(List.of(entry("tt1", "Movie", "2020-01-01", 2020)));

        final var page = service.pageFor(StreamingProvider.DISNEY, USER);

        assertThat(page.included()).hasSize(1);
        assertThat(page.paid()).isEmpty();
    }

    @Test
    void youtubePageHasOnlyPaidSortedByAdded() {
        final var e1 = entry("tt1", "Alpha", "2021-05-05", 2021);
        final var e2 = entry("tt2", "Beta", "2020-01-01", 0);
        when(aggregateService.paid("YouTube Store", USER)).thenReturn(List.of(paid("tt1", "YouTube Store"), paid("tt2", "YouTube Store")));
        when(watchlistCatalogPort.findByImdb(USER, id("tt1"))).thenReturn(Optional.of(e1));
        when(watchlistCatalogPort.findByImdb(USER, id("tt2"))).thenReturn(Optional.of(e2));

        final var page = service.pageFor(StreamingProvider.YOUTUBE, USER);

        assertThat(page.included()).isEmpty();
        assertThat(page.paid()).extracting(PaidEntryDto::added).containsExactly(WatchlistDate.of("2020-01-01"), WatchlistDate.of("2021-05-05"));
        // year 0 becomes the "Not yet released" placeholder
        assertThat(page.paid().get(0).year()).isEqualTo("Not yet released");
    }

    @Test
    void youtubeNeverResolvesFlatrate() {
        when(aggregateService.paid("YouTube Store", USER)).thenReturn(List.of());
        final var page = service.pageFor(StreamingProvider.YOUTUBE, USER);
        assertThat(page.included()).isEmpty();
        verifyNoInteractions(watchlistCatalogPort);
    }
}
