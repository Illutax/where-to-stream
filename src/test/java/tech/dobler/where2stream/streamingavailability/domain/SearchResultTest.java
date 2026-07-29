package tech.dobler.where2stream.streamingavailability.domain;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class SearchResultTest {

    @Test
    void exposesNameAndUrl() {
        final var url = URI.create("https://www.werstreamt.es/the-matrix");
        final var result = new SearchResult("The Matrix", url);

        assertThat(result).extracting(SearchResult::name, SearchResult::url).containsExactly("The Matrix", url);
    }
}
