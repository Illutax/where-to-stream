package tech.dobler.werstreamt.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.werstreamt.configurations.ImdbPosterProperties;
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

/** Network-free tests for the Amazon-CDN resizing helper. (Metadata parsing lives in ImdbTitleClient.) */
@ExtendWith(MockitoExtension.class)
class ImdbPosterSourceTest {

    private static final String POSTER =
            "https://m.media-amazon.com/images/M/MV5BMTk4ODQzNDY3Ml5BMl5BanBnXkFtZTcwODA0NTU4Nw@@._V1_.jpg";

    @Mock
    private HttpClient httpClient;
    @Mock
    private HttpResponse<byte[]> response;
    @Mock
    private TitleMetaService titleMetaService;

    private static ImdbPosterProperties properties() {
        return new ImdbPosterProperties("https://api.graphql.imdb.com/", new ImdbPosterProperties.RateLimit(0),
                100, 50, 600, 85);
    }

    @Test
    void downloadReturnsTheBytesOnASuccessfulResponse() throws Exception {
        doReturn(response).when(httpClient).send(any(), any());
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new byte[]{1, 2, 3});
        final var source = new ImdbPosterSource(properties(), titleMetaService, () -> httpClient);

        assertThat(source.download(POSTER, PosterSize.THUMB)).contains(new byte[]{1, 2, 3});
    }

    @Test
    void downloadReturnsEmptyOnANon200Status() throws Exception {
        doReturn(response).when(httpClient).send(any(), any());
        when(response.statusCode()).thenReturn(404);
        when(response.body()).thenReturn(new byte[0]);
        final var source = new ImdbPosterSource(properties(), titleMetaService, () -> httpClient);

        assertThat(source.download(POSTER, PosterSize.THUMB)).isEmpty();
    }

    @Test
    void downloadReturnsEmptyOnAnIOException() throws Exception {
        doThrow(new IOException("connection reset")).when(httpClient).send(any(), any());
        final var source = new ImdbPosterSource(properties(), titleMetaService, () -> httpClient);

        assertThat(source.download(POSTER, PosterSize.THUMB)).isEmpty();
    }

    @Test
    void downloadNeverCallsOutForABlankPosterPath() {
        final var source = new ImdbPosterSource(properties(), titleMetaService, () -> httpClient);

        assertThat(source.download("  ", PosterSize.THUMB)).isEmpty();
        verifyNoInteractions(httpClient);
    }

    @Test
    void buildsAResizedCdnUrlReplacingAnyExistingTransform() {
        // A base with no transform yet.
        assertThat(ImdbPosterSource.sizedUrl(POSTER, 100, 50))
                .isEqualTo("https://m.media-amazon.com/images/M/MV5BMTk4ODQzNDY3Ml5BMl5BanBnXkFtZTcwODA0NTU4Nw@@._V1_QL50_UX100_.jpg");

        // An already-transformed URL is rebuilt with the requested size/quality.
        final var transformed =
                "https://m.media-amazon.com/images/M/MV5Babc@@._V1_QL75_UY281_CR0,0,190,281_.jpg";
        assertThat(ImdbPosterSource.sizedUrl(transformed, 600, 85))
                .isEqualTo("https://m.media-amazon.com/images/M/MV5Babc@@._V1_QL85_UX600_.jpg");
    }

    @Test
    void leavesAUrlWithoutTheV1MarkerUnchanged() {
        final var plain = "https://example.com/poster.jpg";
        assertThat(ImdbPosterSource.sizedUrl(plain, 100, 50)).isEqualTo(plain);
    }
}
