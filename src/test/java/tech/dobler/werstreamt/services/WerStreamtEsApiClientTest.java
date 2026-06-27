package tech.dobler.werstreamt.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import tech.dobler.werstreamt.domainvalues.AvailabilityType;
import tech.dobler.werstreamt.entities.Availability;
import tech.dobler.werstreamt.entities.QueryResult;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class WerStreamtEsApiClientTest {

    private static final String IMDB_ID = "tt0482571";

    private final WerStreamtEsApiClient client = new WerStreamtEsApiClient();

    /**
     * Mirrors the relevant structure of a werstreamt.es result page:
     * <ul>
     *     <li>Netflix – flatrate only (3 columns, check mark in the first column),</li>
     *     <li>Amazon Prime Video – rent + buy prices (3 columns),</li>
     *     <li>Apple TV – a provider rendered with two offerings (6 columns) which the
     *         parser splits into "(1)" and "(2)".</li>
     * </ul>
     * The {@code <em>} layout is significant: the quality label lives in a nested element
     * (childNode(0).childNode(0)) and the price in the following text node (childNode(1)).
     */
    private static final String HTML = """
            <html><body>
            <div id="avalibility">
              <div class="provider" data-ext-provider-name="Netflix">
                <div class="columns small-4"><i class="fi-check"></i></div>
                <div class="columns small-4"></div>
                <div class="columns small-4"></div>
              </div>
              <div class="provider" data-ext-provider-name="Amazon Prime Video">
                <div class="columns small-4"></div>
                <div class="columns small-4"><em><span>SD</span> 3.99 &euro;</em><em><span>HD</span> 5.99 &euro;</em></div>
                <div class="columns small-4"><em><span>HD</span> 9.99 &euro;</em></div>
              </div>
              <div class="provider" data-ext-provider-name="Apple TV">
                <div class="columns small-4"></div>
                <div class="columns small-4"><em><span>SD</span> 2.99 &euro;</em></div>
                <div class="columns small-4"></div>
                <div class="columns small-4"><i class="fi-check"></i></div>
                <div class="columns small-4"><em><span>4K</span> 7.99 &euro;</em></div>
                <div class="columns small-4"><em><span>HD</span> 12.99 &euro;</em></div>
              </div>
            </div>
            </body></html>
            """;

    private List<QueryResult> parseFixture() {
        final Document document = Jsoup.parse(HTML);
        return client.parse(document, IMDB_ID);
    }

    @Test
    void splitsSixColumnProviderIntoTwoResults() {
        final List<QueryResult> results = parseFixture();

        assertThat(results)
                .extracting(QueryResult::streamingServiceName, QueryResult::imdbId)
                .containsExactly(
                        tuple("Netflix", IMDB_ID),
                        tuple("Amazon Prime Video", IMDB_ID),
                        tuple("Apple TV(1)", IMDB_ID),
                        tuple("Apple TV(2)", IMDB_ID));
    }

    @Test
    void parsesFlatrateProvider() {
        final QueryResult netflix = byName(parseFixture(), "Netflix");

        final var expected = List.of(true, List.of(), true);
        assertThat(netflix)
                .extracting(QueryResult::flatrate, QueryResult::availabilities, QueryResult::isAvailable)
                .isEqualTo(expected);
    }

    @Test
    void parsesRentAndBuyPrices() {
        final QueryResult amazon = byName(parseFixture(), "Amazon Prime Video");

        assertThat(amazon)
                .extracting(QueryResult::flatrate, QueryResult::isAvailable)
                .containsExactly(false, true);

        // NOTE: missing qualities are wrapped in a Price with a null value rather than a
        // null Price (see TODO-18), so we assert on value() here.
        final var expectedRent = Arrays.asList(" 3.99 €", " 5.99 €", null);
        assertThat(byType(amazon, AvailabilityType.RENT))
                .extracting(a -> a.sd().value(), a -> a.hd().value(), a -> a.fourK().value())
                .isEqualTo(expectedRent);

        final var expectedBuy = Arrays.asList(null, " 9.99 €", null);
        assertThat(byType(amazon, AvailabilityType.BUY))
                .extracting(a -> a.sd().value(), a -> a.hd().value(), a -> a.fourK().value())
                .isEqualTo(expectedBuy);
    }

    @Test
    void carriesFlatrateAndPricesOfSecondOfferingFromSixColumnProvider() {
        final List<QueryResult> results = parseFixture();

        final QueryResult first = byName(results, "Apple TV(1)");
        final var expectedFirst = List.of(false, " 2.99 €");
        assertThat(first)
                .extracting(QueryResult::flatrate, q -> byType(q, AvailabilityType.RENT).sd().value())
                .isEqualTo(expectedFirst);

        final QueryResult second = byName(results, "Apple TV(2)");
        final var expectedSecond = List.of(
                true,
                " 7.99 €",
                " 12.99 €");
        assertThat(second)
                .extracting(
                        QueryResult::flatrate,
                        q -> byType(q, AvailabilityType.RENT).fourK().value(),
                        q -> byType(q, AvailabilityType.BUY).hd().value())
                .isEqualTo(expectedSecond);
    }

    @Test
    void returnsEmptyWhenNoProvidersPresent() {
        final List<QueryResult> results = client.parse(Jsoup.parse("<html><body></body></html>"), IMDB_ID);

        assertThat(results).isEmpty();
    }

    private static QueryResult byName(List<QueryResult> results, String name) {
        return results.stream()
                .filter(r -> r.streamingServiceName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No result for " + name));
    }

    private static Availability byType(QueryResult result, AvailabilityType type) {
        return result.availabilities().stream()
                .filter(a -> a.type() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No %s availability in %s".formatted(type, result.streamingServiceName())));
    }
}
