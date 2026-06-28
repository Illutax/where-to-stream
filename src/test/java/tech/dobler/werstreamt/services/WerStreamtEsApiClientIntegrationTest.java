package tech.dobler.werstreamt.services;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import tech.dobler.werstreamt.configurations.WerStreamtProperties;
import tech.dobler.werstreamt.domain.Availability;
import tech.dobler.werstreamt.domain.AvailabilityType;
import tech.dobler.werstreamt.domain.QueryResult;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parses a trimmed copy of a real werstreamt.es detail page (tt0822847 "Priest") to pin the
 * actual page structure — in particular a provider (Prime Video) that lists the same title
 * several times in different languages (the 3·N-columns bug).
 */
class WerStreamtEsApiClientIntegrationTest {

    private static final String IMDB_ID = "tt0822847";

    private final WerStreamtEsApiClient client = new WerStreamtEsApiClient(new RateLimiter(
            new WerStreamtProperties(null, new WerStreamtProperties.Invalidate(28), new WerStreamtProperties.RateLimit(0))));

    private List<QueryResult> parseFixture() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/werstreamt/priest-tt0822847.html")) {
            assertThat(in).as("fixture present").isNotNull();
            final var document = Jsoup.parse(in, "UTF-8", "https://www.werstreamt.es/");
            return client.parse(document, IMDB_ID);
        }
    }

    @Test
    void parsesAllEightProviders() throws Exception {
        final List<QueryResult> results = parseFixture();

        assertThat(results)
                .allSatisfy(r -> assertThat(r.imdbId()).isEqualTo(IMDB_ID))
                .extracting(QueryResult::streamingServiceName)
                .containsOnly("Prime Video", "Sky Store", "Apple TV", "Home of Horror",
                        "MagentaTV", "maxdome", "YouTube Store", "Rakuten TV");
        assertThat(results.stream().map(QueryResult::streamingServiceName).distinct()).hasSize(8);
    }

    @Test
    void primeVideoYieldsThreeLanguageVariantsWithSamePrices() throws Exception {
        final List<QueryResult> prime = parseFixture().stream()
                .filter(r -> r.streamingServiceName().equals("Prime Video"))
                .toList();

        assertThat(prime)
                .hasSize(3)
                .extracting(QueryResult::languages)
                .containsExactlyInAnyOrder(
                        "Deutsch, Englisch (OV)",
                        "Deutsch, Englisch (OV), Französisch, Italienisch, Japanisch, Polnisch, Portugiesisch, Spanisch",
                        null);

        // The null-language variant renders as the plain provider name; the others are labelled.
        assertThat(prime).extracting(QueryResult::label)
                .contains("Prime Video", "Prime Video (Deutsch, Englisch (OV))");

        final QueryResult first = prime.getFirst();
        assertThat(first.flatrate()).isFalse();
        assertThat(byType(first, AvailabilityType.RENT).sd().value()).contains("2.99");
        assertThat(byType(first, AvailabilityType.RENT).hd().value()).contains("3.99");
        assertThat(byType(first, AvailabilityType.BUY).hd().value()).contains("7.99");
    }

    @Test
    void flatrateProviderHasNoPricesAndNoLanguageSuffix() throws Exception {
        final QueryResult hoh = single(parseFixture(), "Home of Horror");

        assertThat(hoh.flatrate()).isTrue();
        assertThat(hoh.isAvailable()).isTrue();
        assertThat(hoh.availabilities()).isEmpty();
        assertThat(hoh.languages()).isNull(); // single offering ⇒ no differentiator
    }

    @Test
    void singleOfferingProvidersParsePrices() throws Exception {
        final List<QueryResult> results = parseFixture();

        final QueryResult sky = single(results, "Sky Store");
        assertThat(byType(sky, AvailabilityType.RENT).hd().value()).contains("3.99");
        assertThat(byType(sky, AvailabilityType.RENT).sd()).isNull();
        assertThat(byType(sky, AvailabilityType.BUY).hd().value()).contains("9.99");
        assertThat(sky.languages()).isNull();

        final QueryResult apple = single(results, "Apple TV");
        assertThat(byType(apple, AvailabilityType.RENT).sd().value()).contains("4.99");
        assertThat(byType(apple, AvailabilityType.RENT).hd().value()).contains("4.99");
    }

    private static QueryResult single(List<QueryResult> results, String name) {
        final var matches = results.stream().filter(r -> r.streamingServiceName().equals(name)).toList();
        assertThat(matches).as("exactly one %s entry", name).hasSize(1);
        return matches.getFirst();
    }

    private static Availability byType(QueryResult result, AvailabilityType type) {
        return result.availabilities().stream()
                .filter(a -> a.type() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No %s availability in %s".formatted(type, result.streamingServiceName())));
    }
}
