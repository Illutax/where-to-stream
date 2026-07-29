package tech.dobler.where2stream.shared.kernel.adapter;

import org.junit.jupiter.api.Test;
import tech.dobler.where2stream.shared.kernel.domain.ReleaseYear;

import static org.assertj.core.api.Assertions.assertThat;

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
}
