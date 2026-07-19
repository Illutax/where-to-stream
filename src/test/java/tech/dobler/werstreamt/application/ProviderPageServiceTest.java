package tech.dobler.werstreamt.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.werstreamt.application.dto.FlatrateEntryDto;
import tech.dobler.werstreamt.application.dto.PaidEntryDto;
import tech.dobler.werstreamt.domain.Availability;
import tech.dobler.werstreamt.domain.ImdbEntry;
import tech.dobler.werstreamt.domain.QueryResult;
import tech.dobler.werstreamt.services.AggregateService;
import tech.dobler.werstreamt.services.ImdbCatalog;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderPageServiceTest {

    @Mock
    private AggregateService aggregateService;
    @Mock
    private ImdbCatalog imdbCatalog;
    @InjectMocks
    private ProviderPageService service;

    private static ImdbEntry entry(String imdbId, String name, String added, int year) {
        return new ImdbEntry(1, name, URI.create("https://www.imdb.com/title/" + imdbId + "/"),
                added, false, year, imdbId);
    }

    private static QueryResult paid(String imdbId, String serviceName) {
        return new QueryResult(imdbId, serviceName, false, List.<Availability>of(), "Deutsch");
    }

    @Test
    void amazonPageHasIncludedSortedByAddedAndPaid() {
        final var later = entry("tt1", "Later", "2021-05-05", 2021);
        final var earlier = entry("tt2", "Earlier", "2020-01-01", 2020);
        final var paidEntry = entry("tt3", "Paid", "2022-02-02", 2022);
        when(aggregateService.contentFor("Prime Video")).thenReturn(
                new AggregateService.ServiceContent(List.of(later, earlier), List.of(paid("tt3", "Prime Video"))));
        when(imdbCatalog.findByImdb("tt3")).thenReturn(Optional.of(paidEntry));

        final var page = service.pageFor(StreamingProvider.AMAZON);

        assertThat(page.provider()).isEqualTo("amazon");
        assertThat(page.included()).extracting(FlatrateEntryDto::added)
                .containsExactly("2020-01-01", "2021-05-05");
        assertThat(page.paid()).extracting(PaidEntryDto::name).containsExactly("Paid");
    }

    @Test
    void flatrateOnlyProviderHasNoPaidAndNeverQueriesPaid() {
        when(aggregateService.included("Disney+")).thenReturn(List.of(entry("tt1", "Movie", "2020-01-01", 2020)));

        final var page = service.pageFor(StreamingProvider.DISNEY);

        assertThat(page.included()).hasSize(1);
        assertThat(page.paid()).isEmpty();
    }

    @Test
    void googlePageHasOnlyPaidSortedByAdded() {
        final var e1 = entry("tt1", "Alpha", "2021-05-05", 2021);
        final var e2 = entry("tt2", "Beta", "2020-01-01", 0);
        when(aggregateService.paid("Google Play")).thenReturn(List.of(paid("tt1", "Google Play"), paid("tt2", "Google Play")));
        when(imdbCatalog.findByImdb("tt1")).thenReturn(Optional.of(e1));
        when(imdbCatalog.findByImdb("tt2")).thenReturn(Optional.of(e2));

        final var page = service.pageFor(StreamingProvider.GOOGLE);

        assertThat(page.included()).isEmpty();
        assertThat(page.paid()).extracting(PaidEntryDto::added).containsExactly("2020-01-01", "2021-05-05");
        // year 0 becomes the "Not yet released" placeholder
        assertThat(page.paid().get(0).year()).isEqualTo("Not yet released");
    }

    @Test
    void googleNeverResolvesFlatrate() {
        when(aggregateService.paid("Google Play")).thenReturn(List.of());
        final var page = service.pageFor(StreamingProvider.GOOGLE);
        assertThat(page.included()).isEmpty();
        verifyNoInteractions(imdbCatalog);
    }
}
