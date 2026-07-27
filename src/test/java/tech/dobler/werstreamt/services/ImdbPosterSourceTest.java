package tech.dobler.werstreamt.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Network-free tests for the IMDb GraphQL response parsing and the Amazon-CDN resizing helper. */
class ImdbPosterSourceTest {

    private static final String POSTER =
            "https://m.media-amazon.com/images/M/MV5BMTk4ODQzNDY3Ml5BMl5BanBnXkFtZTcwODA0NTU4Nw@@._V1_.jpg";

    @Test
    void readsThePrimaryImageUrl() {
        final var json = """
                {"data":{"title":{"primaryImage":{"url":"%s"}}}}""".formatted(POSTER);

        assertThat(ImdbPosterSource.parsePosterUrl(json)).contains(POSTER);
    }

    @Test
    void isEmptyWhenTheTitleHasNoPoster() {
        assertThat(ImdbPosterSource.parsePosterUrl("""
                {"data":{"title":{"primaryImage":null}}}""")).isEmpty();
    }

    @Test
    void isEmptyWhenTheTitleIsUnknown() {
        assertThat(ImdbPosterSource.parsePosterUrl("""
                {"data":{"title":null}}""")).isEmpty();
    }

    @Test
    void isEmptyOnAGraphqlError() {
        assertThat(ImdbPosterSource.parsePosterUrl("""
                {"errors":[{"message":"Cannot query field \\"bogus\\" on type \\"Title\\"."}]}""")).isEmpty();
    }

    @Test
    void isEmptyForMalformedJson() {
        assertThat(ImdbPosterSource.parsePosterUrl("not json")).isEmpty();
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
