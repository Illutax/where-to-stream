package tech.dobler.where2stream.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.where2stream.application.dto.ImdbSearchResultDto;
import tech.dobler.where2stream.domain.ImdbId;
import tech.dobler.where2stream.domain.ReleaseYear;
import tech.dobler.where2stream.persistence.WatchlistEntryRepository;
import tech.dobler.where2stream.services.ImdbSuggestionClient;
import tech.dobler.where2stream.services.ImdbSuggestionClient.ImdbSuggestion;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
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

        assertThat(results).extracting(ImdbSearchResultDto::name, ImdbSearchResultDto::onWatchlist)
                .containsExactly(tuple("The Matrix", true), tuple("The Matrix Resurrections", false));
    }

    @Test
    void isEmptyWhenTheClientFindsNothing() {
        when(imdbSuggestionClient.search("zzz")).thenReturn(List.of());

        assertThat(service.search(USER, "zzz")).isEmpty();
    }
}
