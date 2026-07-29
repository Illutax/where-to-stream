package tech.dobler.where2stream.services;

import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.where2stream.configurations.WerStreamtProperties;
import tech.dobler.where2stream.domain.AvailabilityType;
import tech.dobler.where2stream.domain.Availability;
import tech.dobler.where2stream.shared.domain.ImdbId;
import tech.dobler.where2stream.domain.QueryResult;
import tech.dobler.where2stream.domain.ScrapingException;
import tech.dobler.where2stream.domain.SearchResult;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WerStreamtEsApiClientTest {

    private static final ImdbId IMDB_ID = ImdbId.of("tt0482571");
    private static final String MINUS = "<i class=\"fi-minus-circle\"></i>";
    private static final String CHECK = "<i class=\"fi-check\"></i>";

    // parse() does not hit the network, so the rate limiter is irrelevant here (disabled).
    private final WerStreamtEsApiClient client = new WerStreamtEsApiClient(
            new WerStreamtProperties(new WerStreamtProperties.Invalidate(28), new WerStreamtProperties.RateLimit(0)),
            new RealConnectionFactory());

    @Mock
    private Connection connection;

    private static WerStreamtEsApiClient clientWithFakeConnection(Connection connection) {
        return new WerStreamtEsApiClient(
                new WerStreamtProperties(new WerStreamtProperties.Invalidate(28), new WerStreamtProperties.RateLimit(0)),
                uri -> connection);
    }

    // --- search()/query() network-error handling (connectionFactory seam, network-free) ---

    @Test
    void searchReturnsAnEmptyListWhenTheSiteRespondsWithAnErrorStatus() throws Exception {
        when(connection.get()).thenThrow(new HttpStatusException("Not Found", 404, "https://www.werstreamt.es/filme/"));

        assertThat(clientWithFakeConnection(connection).search("matrix")).isEmpty();
    }

    @Test
    void searchWrapsAnIOExceptionInAScrapingException() throws Exception {
        when(connection.get()).thenThrow(new IOException("connection reset"));

        assertThatThrownBy(() -> clientWithFakeConnection(connection).search("matrix"))
                .isInstanceOf(ScrapingException.class);
    }

    @Test
    void searchParsesResultsFromTheFetchedDocument() throws Exception {
        when(connection.get()).thenReturn(Jsoup.parse("""
                <div class="results"><ul>
                  <li data-contentid="1"><a href="the-matrix"><strong>The Matrix</strong></a></li>
                </ul></div>"""));

        final var results = clientWithFakeConnection(connection).search("matrix");

        assertThat(results).containsExactly(new SearchResult("The Matrix", URI.create("https://www.werstreamt.es/the-matrix")));
    }

    @Test
    void queryParsesResultsFromTheFetchedDocument() throws Exception {
        when(connection.followRedirects(true)).thenReturn(connection);
        when(connection.get()).thenReturn(Jsoup.parse("<div id=\"avalibility\">"
                + "<div class=\"provider\" data-ext-provider-name=\"Netflix\">"
                + "<div class=\"row panel small-collapse available\">"
                + "<div class=\"columns large-5\"><button><strong class=\"title\">Title</strong><br>"
                + "90 Min. | Deutsch<br><span class=\"badges\"></span></button></div>"
                + "<div class=\"columns large-5\"><div class=\"row small-collapse large-uncollapse\">"
                + "<div class=\"columns small-4\"><small>Flatrate</small><br><i class=\"fi-check\"></i></div>"
                + "<div class=\"columns small-4\"><small>Leihen</small><br>-</div>"
                + "<div class=\"columns small-4\"><small>Kaufen</small><br>-</div>"
                + "</div></div></div></div></div>"));

        final var results = clientWithFakeConnection(connection).query(IMDB_ID);

        assertThat(results).extracting(QueryResult::streamingServiceName).containsExactly("Netflix");
    }

    @Test
    void queryReturnsAnEmptyListWhenTheSiteRespondsWithAnErrorStatus() throws Exception {
        when(connection.followRedirects(true)).thenReturn(connection);
        when(connection.get()).thenThrow(new HttpStatusException("Not Found", 404, "https://www.werstreamt.es/filme/"));

        assertThat(clientWithFakeConnection(connection).query(IMDB_ID)).isEmpty();
    }

    @Test
    void queryWrapsAnIOExceptionInAScrapingException() throws Exception {
        when(connection.followRedirects(true)).thenReturn(connection);
        when(connection.get()).thenThrow(new IOException("connection reset"));

        assertThatThrownBy(() -> clientWithFakeConnection(connection).query(IMDB_ID))
                .isInstanceOf(ScrapingException.class);
    }

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
        assertThat(netflix)
                .extracting(QueryResult::flatrate, QueryResult::availabilities, QueryResult::isAvailable,
                        QueryResult::languages)
                .containsExactly(true, List.of(), true, null); // single offering ⇒ no language differentiator
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
        assertThat(rent).extracting(a -> a.sd().value().trim(), a -> a.hd().value().trim(), Availability::fourK)
                .containsExactly("3.99 €", "5.99 €", null);

        final var buy = byType(amazon, AvailabilityType.BUY);
        assertThat(buy).extracting(a -> a.hd().value().trim(), Availability::sd)
                .containsExactly("9.99 €", null);
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

    // --- toSearchResult() (the free-text search() parsing, network-free) ---

    private static org.jsoup.nodes.Element searchListing(String html) {
        return Jsoup.parse("<li data-contentid=\"1\">" + html + "</li>").selectFirst("li");
    }

    @Test
    void toSearchResultParsesNameAndRelativeUrl() {
        final var listing = searchListing("<a href=\"the-matrix\"><strong>The Matrix</strong></a>");

        final Optional<SearchResult> result = WerStreamtEsApiClient.toSearchResult(listing);

        assertThat(result).contains(new SearchResult("The Matrix", URI.create("https://www.werstreamt.es/the-matrix")));
    }

    @Test
    void toSearchResultSkipsAListingWithoutATitle() {
        final var listing = searchListing("<a href=\"x\"></a>");

        assertThat(WerStreamtEsApiClient.toSearchResult(listing)).isEmpty();
    }

    @Test
    void toSearchResultSkipsAnEmptyTitleElement() {
        final var listing = searchListing("<a href=\"x\"><strong></strong></a>");

        assertThat(WerStreamtEsApiClient.toSearchResult(listing)).isEmpty();
    }

    @Test
    void toSearchResultSkipsAListingWithoutALink() {
        final var listing = searchListing("<strong>The Matrix</strong>");

        assertThat(WerStreamtEsApiClient.toSearchResult(listing)).isEmpty();
    }

    @Test
    void capsLanguagesToTheColumnWidth() {
        final var normal = "Deutsch, Englisch (OV)";
        assertThat(WerStreamtEsApiClient.capLanguages(normal)).isEqualTo(normal);

        final var tooLong = "x".repeat(WerStreamtEsApiClient.MAX_LANGUAGES_LENGTH + 50);
        assertThat(WerStreamtEsApiClient.capLanguages(tooLong))
                .hasSize(WerStreamtEsApiClient.MAX_LANGUAGES_LENGTH);
    }
}
