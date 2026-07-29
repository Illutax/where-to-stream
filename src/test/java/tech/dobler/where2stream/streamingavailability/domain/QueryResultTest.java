package tech.dobler.where2stream.streamingavailability.domain;

import org.junit.jupiter.api.Test;
import tech.dobler.where2stream.shared.domain.ImdbId;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueryResultTest {

    private static final ImdbId ID = ImdbId.of("tt1");

    private static QueryResult of(boolean flatrate, List<Availability> availabilities, String languages) {
        return new QueryResult(ID, "Netflix", flatrate, availabilities, languages);
    }

    @Test
    void isAvailableWhenFlatrate() {
        assertThat(of(true, List.of(), null).isAvailable()).isTrue();
    }

    @Test
    void isAvailableWhenNotFlatrateButPricesExist() {
        final var price = new Availability(AvailabilityType.RENT, null, new Price("3.99 €"), null);
        assertThat(of(false, List.of(price), null).isAvailable()).isTrue();
    }

    @Test
    void isNotAvailableWhenNeitherFlatrateNorPriced() {
        assertThat(of(false, List.of(), null).isAvailable()).isFalse();
    }

    @Test
    void labelIsPlainNameWhenLanguagesIsNull() {
        assertThat(of(true, List.of(), null).label()).isEqualTo("Netflix");
    }

    @Test
    void labelIsPlainNameWhenLanguagesIsBlank() {
        assertThat(of(true, List.of(), "  ").label()).isEqualTo("Netflix");
    }

    @Test
    void labelIncludesLanguagesWhenPresent() {
        assertThat(of(true, List.of(), "Deutsch").label()).isEqualTo("Netflix (Deutsch)");
    }
}
