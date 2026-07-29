package tech.dobler.where2stream.shared.kernel.domain;

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

        assertThat(year).extracting(ReleaseYear::isReleased, ReleaseYear::display)
                .containsExactly(false, ReleaseYear.NOT_YET_RELEASED);
    }

    @Test
    void aPositiveYearIsReleasedAndDisplaysAsANumber() {
        final var year = ReleaseYear.of(1999);

        assertThat(year).extracting(ReleaseYear::isReleased, ReleaseYear::display)
                .containsExactly(true, "1999");
    }
}
