package tech.dobler.werstreamt.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgeRatingTest {

    @Test
    void rejectsANullSystem() {
        assertThatThrownBy(() -> new AgeRating(null, "12")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANullLabel() {
        assertThatThrownBy(() -> new AgeRating(AgeRating.RatingSystem.FSK, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsABlankLabel() {
        assertThatThrownBy(() -> new AgeRating(AgeRating.RatingSystem.FSK, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fskBuildsAnFskRating() {
        final var rating = AgeRating.fsk("12");

        assertThat(rating.system()).isEqualTo(AgeRating.RatingSystem.FSK);
        assertThat(rating.label()).isEqualTo("12");
    }

    @Test
    void otherBuildsAFallbackRating() {
        final var rating = AgeRating.other("PG-13");

        assertThat(rating.system()).isEqualTo(AgeRating.RatingSystem.OTHER);
        assertThat(rating.label()).isEqualTo("PG-13");
    }
}
