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
        assertThat(AgeRating.fsk("12")).isEqualTo(new AgeRating(AgeRating.RatingSystem.FSK, "12"));
    }

    @Test
    void otherBuildsAFallbackRating() {
        assertThat(AgeRating.other("PG-13")).isEqualTo(new AgeRating(AgeRating.RatingSystem.OTHER, "PG-13"));
    }
}
