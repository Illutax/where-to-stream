package tech.dobler.werstreamt.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Network-free tests for the Amazon-CDN resizing helper. (Metadata parsing lives in ImdbTitleClient.) */
class ImdbPosterSourceTest {

    private static final String POSTER =
            "https://m.media-amazon.com/images/M/MV5BMTk4ODQzNDY3Ml5BMl5BanBnXkFtZTcwODA0NTU4Nw@@._V1_.jpg";

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
