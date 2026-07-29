package tech.dobler.where2stream.shared.kernel.adapter;

import org.junit.jupiter.api.Test;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.shared.kernel.domain.ReleaseYear;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two shared-kernel {@code AttributeConverter}s share the same null-safe shape (null in, null out;
 * value in, mapped value out) — covered together since each branch is otherwise a one-liner.
 */
class ConvertersTest {

    @Test
    void releaseYearConverterRoundTripsANullValue() {
        final var converter = new ReleaseYearConverter();

        assertThat(converter)
                .extracting(c -> c.convertToDatabaseColumn(null), c -> c.convertToEntityAttribute(null))
                .containsOnlyNulls();
    }

    @Test
    void releaseYearConverterRoundTripsARealValue() {
        final var converter = new ReleaseYearConverter();

        assertThat(converter)
                .extracting(c -> c.convertToDatabaseColumn(ReleaseYear.of(1999)), c -> c.convertToEntityAttribute(1999))
                .containsExactly(1999, ReleaseYear.of(1999));
    }

    @Test
    void imdbIdConverterRoundTripsANullValue() {
        final var converter = new ImdbIdConverter();

        assertThat(converter)
                .extracting(c -> c.convertToDatabaseColumn(null), c -> c.convertToEntityAttribute(null))
                .containsOnlyNulls();
    }

    @Test
    void imdbIdConverterRoundTripsARealValue() {
        final var converter = new ImdbIdConverter();

        assertThat(converter)
                .extracting(c -> c.convertToDatabaseColumn(ImdbId.of("tt1")), c -> c.convertToEntityAttribute("tt1"))
                .containsExactly("tt1", ImdbId.of("tt1"));
    }
}
