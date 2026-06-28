package tech.dobler.werstreamt.services;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import tech.dobler.werstreamt.configurations.WerStreamtProperties;
import tech.dobler.werstreamt.domain.AvailabilityType;
import tech.dobler.werstreamt.domain.Availability;
import tech.dobler.werstreamt.domain.QueryResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class WerStreamtEsApiClientTest {

    private static final String IMDB_ID = "tt0482571";
    private static final String MINUS = "<i class=\"fi-minus-circle\"></i>";
    private static final String CHECK = "<i class=\"fi-check\"></i>";

    // parse() does not hit the network, so the rate limiter is irrelevant here (disabled).
    private final WerStreamtEsApiClient client = new WerStreamtEsApiClient(new RateLimiter(
            new WerStreamtProperties(null, new WerStreamtProperties.Invalidate(28), new WerStreamtProperties.RateLimit(0))));

    // --- fixture builders mirroring the real werstreamt.es per-listing structure ---

    private static String price(String quality, String value) {
        return "<em><strong>" + quality + "</strong> " + value + "</em>";
    }

    /** One listing row: title/meta block + the 3 Flatrate/Leihen/Kaufen columns. */
    private static String offering(String meta, String flatrate, String rent, String buy) {
        return "<div class=\"row panel small-collapse available\">"
                + "<div class=\"columns large-5\"><button>"
                + "<strong class=\"title\">Title</strong><br>" + meta + "<br>"
                + "<span class=\"badges\"></span></button></div>"
                + "<div class=\"columns large-5\"><div class=\"row small-collapse large-uncollapse\">"
                + "<div class=\"columns small-4\"><small>Flatrate</small><br>" + flatrate + "</div>"
                + "<div class=\"columns small-4\"><small>Leihen</small><br>" + rent + "</div>"
                + "<div class=\"columns small-4\"><small>Kaufen</small><br>" + buy + "</div>"
                + "</div></div></div>";
    }

    private static String provider(String name, String... offerings) {
        return "<div class=\"provider\" data-ext-provider-name=\"" + name + "\">"
                + "<div class=\"large-10 columns\">" + String.join("", offerings) + "</div></div>";
    }

    private List<QueryResult> parse(String... providers) {
        final var html = "<div id=\"avalibility\">" + String.join("", providers) + "</div>";
        return client.parse(Jsoup.parse(html), IMDB_ID);
    }

    // --- tests ---

    @Test
    void parsesFlatrateProvider() {
        final var results = parse(provider("Netflix",
                offering("90 Min. | Deutsch", CHECK, "-", "-")));

        final var netflix = single(results, "Netflix");
        assertThat(netflix.flatrate()).isTrue();
        assertThat(netflix.availabilities()).isEmpty();
        assertThat(netflix.isAvailable()).isTrue();
        assertThat(netflix.languages()).isNull(); // single offering ⇒ no differentiator
    }

    @Test
    void parsesRentAndBuyPrices() {
        final var results = parse(provider("Amazon Prime Video",
                offering("120 Min. | Deutsch", MINUS,
                        price("SD", "3.99 €") + "<br>" + price("HD", "5.99 €"),
                        price("HD", "9.99 €"))));

        final var amazon = single(results, "Amazon Prime Video");
        assertThat(amazon.flatrate()).isFalse();

        final var rent = byType(amazon, AvailabilityType.RENT);
        assertThat(rent.sd().value()).contains("3.99");
        assertThat(rent.hd().value()).contains("5.99");
        assertThat(rent.fourK()).isNull();

        final var buy = byType(amazon, AvailabilityType.BUY);
        assertThat(buy.hd().value()).contains("9.99");
        assertThat(buy.sd()).isNull();
    }

    @Test
    void collapsesIdenticalOfferings() {
        final var sameRent = price("HD", "3.99 €");
        final var results = parse(provider("Disney+",
                offering("90 Min. | Deutsch", MINUS, sameRent, "-"),
                offering("90 Min. | Deutsch", MINUS, sameRent, "-")));

        // identical flatrate + prices + language ⇒ merged into one
        final var disney = single(results, "Disney+");
        assertThat(disney.languages()).isNull();
    }

    @Test
    void keepsLanguageVariantsWithSamePricesAndLabelsThem() {
        final var rent = price("HD", "3.99 €");
        final var results = parse(provider("Prime Video",
                offering("87 Min. | Deutsch", MINUS, rent, "-"),
                offering("88 Min. | Englisch", MINUS, rent, "-")));

        assertThat(results)
                .extracting(QueryResult::streamingServiceName, QueryResult::languages, QueryResult::label)
                .containsExactly(
                        tuple("Prime Video", "Deutsch", "Prime Video (Deutsch)"),
                        tuple("Prime Video", "Englisch", "Prime Video (Englisch)"));
    }

    @Test
    void keepsPriceDistinctOfferings() {
        final var results = parse(provider("Apple TV",
                offering("90 Min. | Deutsch", MINUS, price("SD", "2.99 €"), "-"),
                offering("90 Min. | Englisch", MINUS, price("HD", "4.99 €"), "-")));

        assertThat(results).hasSize(2)
                .allSatisfy(r -> assertThat(r.streamingServiceName()).isEqualTo("Apple TV"));
        assertThat(byType(results.get(0), AvailabilityType.RENT).sd().value()).contains("2.99");
        assertThat(byType(results.get(1), AvailabilityType.RENT).hd().value()).contains("4.99");
    }

    @Test
    void skipsProviderWithUnexpectedColumnCount() {
        // "Broken" uses the flat layout with 2 columns (no listing rows) ⇒ skipped via fallback.
        final var broken = "<div class=\"provider\" data-ext-provider-name=\"Broken\">"
                + "<div class=\"columns small-4\"></div><div class=\"columns small-4\"></div></div>";

        final var results = parse(broken, provider("Netflix", offering("90 Min. | Deutsch", CHECK, "-", "-")));

        assertThat(results).extracting(QueryResult::streamingServiceName).containsExactly("Netflix");
    }

    @Test
    void skipsMalformedEmWithoutCrashing() {
        final var results = parse(provider("Weird",
                offering("90 Min. | Deutsch", MINUS, "<em></em>", "-")));

        final var weird = single(results, "Weird");
        assertThat(weird.availabilities()).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoProvidersPresent() {
        assertThat(client.parse(Jsoup.parse("<html><body></body></html>"), IMDB_ID)).isEmpty();
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
