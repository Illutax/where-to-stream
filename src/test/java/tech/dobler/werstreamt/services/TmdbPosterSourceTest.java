package tech.dobler.werstreamt.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.werstreamt.configurations.TmdbProperties;
import tech.dobler.werstreamt.domain.ImdbId;
import tech.dobler.werstreamt.domain.PosterSize;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TmdbPosterSourceTest {

    @Mock
    private HttpClient httpClient;
    @Mock
    private HttpResponse<String> stringResponse;
    @Mock
    private HttpResponse<byte[]> bytesResponse;

    private static TmdbProperties activeProperties() {
        return new TmdbProperties(true, "api-key", "https://api.themoviedb.org/3", "https://image.tmdb.org/t/p",
                new TmdbProperties.RateLimit(0));
    }

    @Test
    void findPosterPathReturnsEmptyWithoutCallingOutWhenTmdbIsInactive() {
        final var inactive = new TmdbProperties(false, "", "https://api.themoviedb.org/3",
                "https://image.tmdb.org/t/p", new TmdbProperties.RateLimit(0));
        final var source = new TmdbPosterSource(inactive, () -> httpClient);

        assertThat(source.findPosterPath(ImdbId.of("tt1"))).isEmpty();
        verifyNoInteractions(httpClient);
    }

    @Test
    void findPosterPathReturnsTheParsedPathOnASuccessfulResponse() throws Exception {
        doReturn(stringResponse).when(httpClient).send(any(), any());
        when(stringResponse.statusCode()).thenReturn(200);
        when(stringResponse.body()).thenReturn("""
                {"movie_results":[{"poster_path":"/abc.jpg"}]}""");
        final var source = new TmdbPosterSource(activeProperties(), () -> httpClient);

        assertThat(source.findPosterPath(ImdbId.of("tt1"))).contains("/abc.jpg");
    }

    @Test
    void findPosterPathReturnsEmptyOnANon200Status() throws Exception {
        doReturn(stringResponse).when(httpClient).send(any(), any());
        when(stringResponse.statusCode()).thenReturn(500);
        final var source = new TmdbPosterSource(activeProperties(), () -> httpClient);

        assertThat(source.findPosterPath(ImdbId.of("tt1"))).isEmpty();
    }

    @Test
    void findPosterPathReturnsEmptyOnAnIOException() throws Exception {
        doThrow(new IOException("connection reset")).when(httpClient).send(any(), any());
        final var source = new TmdbPosterSource(activeProperties(), () -> httpClient);

        assertThat(source.findPosterPath(ImdbId.of("tt1"))).isEmpty();
    }

    @Test
    void downloadReturnsTheBytesOnASuccessfulResponse() throws Exception {
        doReturn(bytesResponse).when(httpClient).send(any(), any());
        when(bytesResponse.statusCode()).thenReturn(200);
        when(bytesResponse.body()).thenReturn(new byte[]{1, 2, 3});
        final var source = new TmdbPosterSource(activeProperties(), () -> httpClient);

        assertThat(source.download("/abc.jpg", PosterSize.THUMB)).contains(new byte[]{1, 2, 3});
    }

    @Test
    void downloadReturnsEmptyOnAnEmptyBody() throws Exception {
        doReturn(bytesResponse).when(httpClient).send(any(), any());
        when(bytesResponse.statusCode()).thenReturn(200);
        when(bytesResponse.body()).thenReturn(new byte[0]);
        final var source = new TmdbPosterSource(activeProperties(), () -> httpClient);

        assertThat(source.download("/abc.jpg", PosterSize.THUMB)).isEmpty();
    }

    @Test
    void downloadReturnsEmptyOnAnIOException() throws Exception {
        doThrow(new IOException("connection reset")).when(httpClient).send(any(), any());
        final var source = new TmdbPosterSource(activeProperties(), () -> httpClient);

        assertThat(source.download("/abc.jpg", PosterSize.THUMB)).isEmpty();
    }

    @Test
    void downloadNeverCallsOutForABlankPosterPath() {
        final var source = new TmdbPosterSource(activeProperties(), () -> httpClient);

        assertThat(source.download(null, PosterSize.THUMB)).isEmpty();
        verifyNoInteractions(httpClient);
    }

    @Test
    void extractsTheMoviePosterPath() {
        final var json = """
                {"movie_results":[{"id":1,"poster_path":"/abc.jpg"}],"tv_results":[],"person_results":[]}""";
        assertThat(TmdbPosterSource.parsePosterPath(json)).contains("/abc.jpg");
    }

    @Test
    void fallsBackToTheTvPosterPathWhenThereIsNoMovie() {
        final var json = """
                {"movie_results":[],"tv_results":[{"poster_path":"/tv.jpg"}]}""";
        assertThat(TmdbPosterSource.parsePosterPath(json)).contains("/tv.jpg");
    }

    @Test
    void isEmptyWhenNoResultHasAPoster() {
        assertThat(TmdbPosterSource.parsePosterPath("""
                {"movie_results":[],"tv_results":[]}""")).isEmpty();
        assertThat(TmdbPosterSource.parsePosterPath("""
                {"movie_results":[{"id":1,"poster_path":null}]}""")).isEmpty();
    }

    @Test
    void isEmptyForMalformedJson() {
        assertThat(TmdbPosterSource.parsePosterPath("not json")).isEmpty();
    }
}
