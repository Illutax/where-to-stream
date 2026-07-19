package tech.dobler.werstreamt.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.werstreamt.domain.Availability;
import tech.dobler.werstreamt.domain.ImdbEntry;
import tech.dobler.werstreamt.domain.QueryResult;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AggregateServiceTest {

    @Mock
    private ImdbCatalog imdbCatalog;
    @Mock
    private StreamInfoService streamInfoService;
    @InjectMocks
    private AggregateService service;

    private static ImdbEntry entry(String imdbId, String name) {
        return new ImdbEntry(1, name, URI.create("https://www.imdb.com/title/" + imdbId + "/"),
                "2020-01-01", false, 2020, imdbId);
    }

    private static QueryResult flatrate(String imdbId, String serviceName, String languages) {
        return new QueryResult(imdbId, serviceName, true, List.<Availability>of(), languages);
    }

    private static QueryResult paid(String imdbId, String serviceName) {
        return new QueryResult(imdbId, serviceName, false, List.<Availability>of(), null);
    }

    /** Catalogue of tt1 (Netflix flatrate, two language variants), tt2 (Netflix paid), tt3 (Disney+ flatrate). */
    private void givenCatalogue() {
        when(imdbCatalog.findAll()).thenReturn(List.of(entry("tt1", "A"), entry("tt2", "B"), entry("tt3", "C")));
        when(streamInfoService.resolveAll(List.of("tt1", "tt2", "tt3"))).thenReturn(Map.of(
                "tt1", List.of(flatrate("tt1", "Netflix", "Deutsch"), flatrate("tt1", "Netflix", "English")),
                "tt2", List.of(paid("tt2", "Netflix")),
                "tt3", List.of(flatrate("tt3", "Disney+", null))));
    }

    @Test
    void includedReturnsFlatrateTitlesForTheServiceDeduplicatedAcrossLanguageVariants() {
        givenCatalogue();
        when(imdbCatalog.findByImdb("tt1")).thenReturn(Optional.of(entry("tt1", "A")));

        // tt1 is on Netflix flatrate in two languages -> distinct() collapses it to one entry.
        assertThat(service.included("Netflix")).extracting(ImdbEntry::imdbId).containsExactly("tt1");
    }

    @Test
    void paidReturnsOnlyNonFlatrateTitlesForTheService() {
        givenCatalogue();

        assertThat(service.paid("Netflix")).extracting(QueryResult::imdbId).containsExactly("tt2");
    }

    @Test
    void contentForDerivesBothListsFromASingleResolve() {
        givenCatalogue();
        when(imdbCatalog.findByImdb("tt1")).thenReturn(Optional.of(entry("tt1", "A")));

        final var content = service.contentFor("Netflix");

        assertThat(content.included()).extracting(ImdbEntry::imdbId).containsExactly("tt1");
        assertThat(content.paid()).extracting(QueryResult::imdbId).containsExactly("tt2");
    }

    @Test
    void getAllFlattensEveryResolvedResult() {
        givenCatalogue();

        assertThat(service.getAll()).hasSize(4); // 2x tt1 + 1x tt2 + 1x tt3
    }

    @Test
    void includedIsEmptyForAServiceWithNoFlatrateTitles() {
        givenCatalogue();

        assertThat(service.included("Prime Video")).isEmpty();
    }
}
