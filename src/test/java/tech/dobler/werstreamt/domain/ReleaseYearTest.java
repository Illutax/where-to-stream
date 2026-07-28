package tech.dobler.werstreamt.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReleaseYearTest {

    @Test
    void rejectsANegativeYear() {
        assertThatThrownBy(() -> ReleaseYear.of(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroMeansNotYetReleased() {
        final var year = ReleaseYear.of(0);

        assertThat(year.isReleased()).isFalse();
        assertThat(year.display()).isEqualTo(ReleaseYear.NOT_YET_RELEASED);
    }

    @Test
    void aPositiveYearIsReleasedAndDisplaysAsANumber() {
        final var year = ReleaseYear.of(1999);

        assertThat(year.isReleased()).isTrue();
        assertThat(year.display()).isEqualTo("1999");
    }
}
