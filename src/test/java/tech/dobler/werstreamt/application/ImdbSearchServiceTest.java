package tech.dobler.werstreamt.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.werstreamt.domain.ImdbId;
import tech.dobler.werstreamt.domain.ReleaseYear;
import tech.dobler.werstreamt.persistence.WatchlistEntryRepository;
import tech.dobler.werstreamt.services.ImdbSuggestionClient;
import tech.dobler.werstreamt.services.ImdbSuggestionClient.ImdbSuggestion;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImdbSearchServiceTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private ImdbSuggestionClient imdbSuggestionClient;
    @Mock
    private WatchlistEntryRepository watchlistEntries;
    @InjectMocks
    private ImdbSearchService service;

    @Test
    void flagsHitsAlreadyOnTheUsersWatchlist() {
        when(imdbSuggestionClient.search("matrix")).thenReturn(List.of(
                new ImdbSuggestion(ImdbId.of("tt0133093"), "The Matrix", ReleaseYear.of(1999)),
                new ImdbSuggestion(ImdbId.of("tt10838180"), "The Matrix Resurrections", ReleaseYear.of(2021))));
        when(watchlistEntries.existsByUserIdAndImdbId(USER, ImdbId.of("tt0133093"))).thenReturn(true);
        when(watchlistEntries.existsByUserIdAndImdbId(USER, ImdbId.of("tt10838180"))).thenReturn(false);

        final var results = service.search(USER, "matrix");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).onWatchlist()).isTrue();
        assertThat(results.get(0).name()).isEqualTo("The Matrix");
        assertThat(results.get(1).onWatchlist()).isFalse();
    }

    @Test
    void isEmptyWhenTheClientFindsNothing() {
        when(imdbSuggestionClient.search("zzz")).thenReturn(List.of());

        assertThat(service.search(USER, "zzz")).isEmpty();
    }
}
