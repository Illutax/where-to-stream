package tech.dobler.where2stream.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.where2stream.configurations.ImdbPosterProperties;
import tech.dobler.where2stream.domain.AgeRating.RatingSystem;
import tech.dobler.where2stream.domain.ImdbId;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/** Network-free tests for parsing the one IMDb GraphQL response into poster URL + age rating. */
@ExtendWith(MockitoExtension.class)
class ImdbTitleClientTest {

    @Mock
    private HttpClient httpClient;
    @Mock
    private HttpResponse<String> response;

    private static ImdbPosterProperties properties() {
        return new ImdbPosterProperties("https://api.graphql.imdb.com/", new ImdbPosterProperties.RateLimit(0),
                100, 50, 600, 85);
    }

    @Test
    void fetchReturnsTheParsedDataOnASuccessfulResponse() throws Exception {
        doReturn(response).when(httpClient).send(any(), any());
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"data":{"title":{"primaryImage":{"url":"%s"}}}}""".formatted(POSTER));
        final var client = new ImdbTitleClient(properties(), () -> httpClient);

        final var result = client.fetch(ImdbId.of("tt0133093"));

        assertThat(result).isPresent();
        assertThat(result.get().posterUrl()).isEqualTo(POSTER);
    }

    @Test
    void fetchReturnsEmptyOnANon200Status() throws Exception {
        doReturn(response).when(httpClient).send(any(), any());
        when(response.statusCode()).thenReturn(500);
        final var client = new ImdbTitleClient(properties(), () -> httpClient);

        assertThat(client.fetch(ImdbId.of("tt0133093"))).isEmpty();
    }

    @Test
    void fetchReturnsEmptyOnAnIOException() throws Exception {
        doThrow(new IOException("connection reset")).when(httpClient).send(any(), any());
        final var client = new ImdbTitleClient(properties(), () -> httpClient);

        assertThat(client.fetch(ImdbId.of("tt0133093"))).isEmpty();
    }

    @Test
    void fetchRestoresTheInterruptFlagOnInterruptedException() throws Exception {
        doThrow(new InterruptedException()).when(httpClient).send(any(), any());
        final var client = new ImdbTitleClient(properties(), () -> httpClient);

        try {
            assertThat(client.fetch(ImdbId.of("tt0133093"))).isEmpty();
            assertThat(Thread.interrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private static final String POSTER =
            "https://m.media-amazon.com/images/M/MV5Babc@@._V1_.jpg";

    @Test
    void readsThePosterUrlAndPrefersTheGermanFskRating() {
        final var json = """
                {"data":{"title":{"primaryImage":{"url":"%s"},"certificate":{"rating":"R"},
                 "certificates":{"edges":[
                   {"node":{"rating":"12","country":{"id":"FR"}}},
                   {"node":{"rating":"16","country":{"id":"DE"}}}]}}}}""".formatted(POSTER);

        final var data = ImdbTitleClient.parse(json);

        assertThat(data).extracting(ImdbTitleClient.ImdbTitleData::posterUrl,
                        d -> d.rating().system(), d -> d.rating().label())
                .containsExactly(POSTER, RatingSystem.FSK, "16");
    }

    @Test
    void fallsBackToThePrimaryCertificateWhenThereIsNoGermanRating() {
        final var json = """
                {"data":{"title":{"primaryImage":{"url":"%s"},"certificate":{"rating":"PG-13"},
                 "certificates":{"edges":[{"node":{"rating":"14A","country":{"id":"CA"}}}]}}}}""".formatted(POSTER);

        final var data = ImdbTitleClient.parse(json);

        assertThat(data).extracting(d -> d.rating().system(), d -> d.rating().label())
                .containsExactly(RatingSystem.OTHER, "PG-13");
    }

    @Test
    void readsTheGermanTitleFromTheDeAka() {
        final var json = """
                {"data":{"title":{"primaryImage":{"url":"%s"},
                 "akas":{"edges":[
                   {"node":{"text":"Up","country":{"id":"GB"}}},
                   {"node":{"text":"Oben","country":{"id":"DE"}}}]}}}}""".formatted(POSTER);

        assertThat(ImdbTitleClient.parse(json).germanTitle()).isEqualTo("Oben");
    }

    @Test
    void hasNoGermanTitleWhenThereIsNoDeAka() {
        final var json = """
                {"data":{"title":{"akas":{"edges":[{"node":{"text":"Up","country":{"id":"GB"}}}]}}}}""";

        assertThat(ImdbTitleClient.parse(json).germanTitle()).isNull();
    }

    @Test
    void hasNoRatingWhenThereIsNeitherGermanNorPrimary() {
        final var json = """
                {"data":{"title":{"primaryImage":{"url":"%s"},"certificate":null,"certificates":{"edges":[]}}}}""".formatted(POSTER);

        final var data = ImdbTitleClient.parse(json);

        assertThat(data).extracting(ImdbTitleClient.ImdbTitleData::posterUrl, ImdbTitleClient.ImdbTitleData::rating)
                .containsExactly(POSTER, null);
    }

    @Test
    void isAllNullForNoPosterUnknownTitleOrMalformedJson() {
        assertThat(ImdbTitleClient.parse("""
                {"data":{"title":{"primaryImage":null,"certificates":{"edges":[]}}}}""").posterUrl()).isNull();
        assertThat(ImdbTitleClient.parse("""
                {"data":{"title":null}}""").posterUrl()).isNull();
        assertThat(ImdbTitleClient.parse("""
                {"errors":[{"message":"boom"}]}""").rating()).isNull();
        final var malformed = ImdbTitleClient.parse("not json");
        assertThat(malformed).extracting(ImdbTitleClient.ImdbTitleData::posterUrl, ImdbTitleClient.ImdbTitleData::rating)
                .containsOnlyNulls();
    }
}
