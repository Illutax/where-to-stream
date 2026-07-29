package tech.dobler.where2stream.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImdbIdTest {

    @Test
    void rejectsANullValue() {
        assertThatThrownBy(() -> new ImdbId(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAValueNotMatchingTheTtFormat() {
        assertThatThrownBy(() -> ImdbId.of("not-an-id")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsAWellFormedId() {
        final var id = ImdbId.of("tt0482571");

        assertThat(id.value()).isEqualTo("tt0482571");
        assertThat(id).hasToString("tt0482571");
    }
}
