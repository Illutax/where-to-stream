package tech.dobler.werstreamt.services;

import org.junit.jupiter.api.Test;
import tech.dobler.werstreamt.domain.ImdbId;

import static org.assertj.core.api.Assertions.assertThat;

/** Network-free tests for parsing IMDb's suggestion/typeahead JSON payload. */
class ImdbSuggestionClientTest {

    @Test
    void keepsOnlyTitleHitsAndReadsNameAndYear() {
        final var json = """
                {"d":[
                  {"id":"tt0133093","l":"The Matrix","y":1999,"q":"feature"},
                  {"id":"nm10550834","l":"Amélie Hoeferle"},
                  {"id":"co0012345","l":"Matrix Studios"}
                ]}""";

        final var results = ImdbSuggestionClient.parse(json, 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).imdbId()).isEqualTo(ImdbId.of("tt0133093"));
        assertThat(results.get(0).name()).isEqualTo("The Matrix");
        assertThat(results.get(0).year().value()).isEqualTo(1999);
    }

    @Test
    void defaultsToYearZeroWhenTheYearIsAbsent() {
        final var json = """
                {"d":[{"id":"tt31998838","l":"Matrix 5"}]}""";

        final var results = ImdbSuggestionClient.parse(json, 10);

        assertThat(results.get(0).year().value()).isZero();
    }

    @Test
    void capsResultsAtMaxResults() {
        final var json = """
                {"d":[
                  {"id":"tt0000001","l":"One"},
                  {"id":"tt0000002","l":"Two"},
                  {"id":"tt0000003","l":"Three"}
                ]}""";

        assertThat(ImdbSuggestionClient.parse(json, 2)).hasSize(2);
    }

    @Test
    void skipsAHitWithNoNameOrNoId() {
        final var json = """
                {"d":[{"id":"tt0000001"},{"l":"No id"},{"id":"tt0000002","l":""}]}""";

        assertThat(ImdbSuggestionClient.parse(json, 10)).isEmpty();
    }

    @Test
    void isEmptyForMalformedJsonOrAMissingDArray() {
        assertThat(ImdbSuggestionClient.parse("not json", 10)).isEmpty();
        assertThat(ImdbSuggestionClient.parse("{}", 10)).isEmpty();
    }
}
