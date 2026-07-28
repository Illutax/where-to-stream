package tech.dobler.werstreamt.services;

import org.junit.jupiter.api.Test;
import tech.dobler.werstreamt.domain.ImdbId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Network-free tests for parsing IMDb's suggestion/typeahead JSON payload. */
class ImdbSuggestionClientTest {

    /**
     * A query starting with a character that isn't safe unescaped in a URI path segment (a bare
     * "%", a quote, a backslash, a space, …) used to make {@code URI.create} throw
     * {@code IllegalArgumentException} — reproduced directly here, not just indirectly via
     * {@code search()}'s catch-all, so a future regression fails loudly at the source.
     */
    @Test
    void buildUriNeverThrowsForAWkwardLeadingCharacters() {
        for (String query : new String[] { "%", "%20", "\"quoted\"", "back\\slash", " leading space", "?" }) {
            assertThatCode(() -> ImdbSuggestionClient.buildUri("https://v2.sg.media-imdb.com/suggestion", query))
                    .as("query starting with %s", query)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void buildUriEncodesTheLeadingCharacterAndTheQuery() {
        final var uri = ImdbSuggestionClient.buildUri("https://v2.sg.media-imdb.com/suggestion", "%matrix");

        assertThat(uri.toString()).isEqualTo("https://v2.sg.media-imdb.com/suggestion/%25/%25matrix.json");
    }

    @Test
    void buildUriLowercasesAPlainLeadingLetter() {
        final var uri = ImdbSuggestionClient.buildUri("https://v2.sg.media-imdb.com/suggestion", "Matrix");

        assertThat(uri.toString()).isEqualTo("https://v2.sg.media-imdb.com/suggestion/m/Matrix.json");
    }

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
