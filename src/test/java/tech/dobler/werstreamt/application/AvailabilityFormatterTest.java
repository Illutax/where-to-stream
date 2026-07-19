package tech.dobler.werstreamt.application;

import org.junit.jupiter.api.Test;
import tech.dobler.werstreamt.domain.Availability;
import tech.dobler.werstreamt.domain.AvailabilityType;
import tech.dobler.werstreamt.domain.Price;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityFormatterTest {

    private static Price price(String value) {
        return new Price(value);
    }

    @Test
    void emptyListYieldsEmptyString() {
        assertThat(AvailabilityFormatter.prettyPrint(List.of())).isEmpty();
    }

    @Test
    void rentIsLabelledLeihen() {
        final var a = new Availability(AvailabilityType.RENT, null, price("3,99"), null);
        assertThat(AvailabilityFormatter.prettyPrint(List.of(a))).isEqualTo("leihen: HD: 3,99 ");
    }

    @Test
    void buyIsLabelledKaufen() {
        final var a = new Availability(AvailabilityType.BUY, price("9,99"), null, null);
        assertThat(AvailabilityFormatter.prettyPrint(List.of(a))).isEqualTo("kaufen: SD: 9,99 ");
    }

    @Test
    void allResolutionsAreRenderedInFourKHdSdOrder() {
        final var a = new Availability(AvailabilityType.BUY, price("9,99"), price("12,99"), price("15,99"));
        assertThat(AvailabilityFormatter.prettyPrint(List.of(a)))
                .isEqualTo("kaufen: 4k: 15,99 HD: 12,99 SD: 9,99 ");
    }

    @Test
    void multipleAvailabilitiesAreJoinedByComma() {
        final var rent = new Availability(AvailabilityType.RENT, null, price("3,99"), null);
        final var buy = new Availability(AvailabilityType.BUY, null, price("9,99"), null);
        assertThat(AvailabilityFormatter.prettyPrint(List.of(rent, buy)))
                .isEqualTo("leihen: HD: 3,99 , kaufen: HD: 9,99 ");
    }
}
