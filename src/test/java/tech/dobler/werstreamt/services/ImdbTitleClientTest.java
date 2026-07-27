package tech.dobler.werstreamt.services;

import org.junit.jupiter.api.Test;
import tech.dobler.werstreamt.domain.AgeRating.RatingSystem;

import static org.assertj.core.api.Assertions.assertThat;

/** Network-free tests for parsing the one IMDb GraphQL response into poster URL + age rating. */
class ImdbTitleClientTest {

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

        assertThat(data.posterUrl()).isEqualTo(POSTER);
        assertThat(data.rating().system()).isEqualTo(RatingSystem.FSK);
        assertThat(data.rating().label()).isEqualTo("16");
    }

    @Test
    void fallsBackToThePrimaryCertificateWhenThereIsNoGermanRating() {
        final var json = """
                {"data":{"title":{"primaryImage":{"url":"%s"},"certificate":{"rating":"PG-13"},
                 "certificates":{"edges":[{"node":{"rating":"14A","country":{"id":"CA"}}}]}}}}""".formatted(POSTER);

        final var data = ImdbTitleClient.parse(json);

        assertThat(data.rating().system()).isEqualTo(RatingSystem.OTHER);
        assertThat(data.rating().label()).isEqualTo("PG-13");
    }

    @Test
    void hasNoRatingWhenThereIsNeitherGermanNorPrimary() {
        final var json = """
                {"data":{"title":{"primaryImage":{"url":"%s"},"certificate":null,"certificates":{"edges":[]}}}}""".formatted(POSTER);

        final var data = ImdbTitleClient.parse(json);

        assertThat(data.posterUrl()).isEqualTo(POSTER);
        assertThat(data.rating()).isNull();
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
        assertThat(malformed.posterUrl()).isNull();
        assertThat(malformed.rating()).isNull();
    }
}
