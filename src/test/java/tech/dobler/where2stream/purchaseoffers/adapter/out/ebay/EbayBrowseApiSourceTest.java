package tech.dobler.where2stream.purchaseoffers.adapter.out.ebay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.where2stream.purchaseoffers.adapter.out.ebay.EbayBrowseApiSource.BuyingOption;
import tech.dobler.where2stream.purchaseoffers.EbayPropertiesFixture;
import tech.dobler.where2stream.purchaseoffers.domain.Marketplace;
import tech.dobler.where2stream.purchaseoffers.domain.Offer;
import tech.dobler.where2stream.purchaseoffers.domain.OfferPrice;
import tech.dobler.where2stream.purchaseoffers.domain.OfferSourceUnavailableException;
import tech.dobler.where2stream.purchaseoffers.domain.TitleOffers;
import tech.dobler.where2stream.purchaseoffers.domain.UpstreamQuotaExhaustedException;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.shared.platform.time.TimeService;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Network-free tests for the Browse API adapter.
 *
 * <p>The JSON literals below are built from eBay's documentation, not from a captured response —
 * the developer account was still pending when this was written. They therefore pin <em>our</em>
 * mapping, not eBay's actual payload; the first real response has to be checked against them.
 */
@ExtendWith(MockitoExtension.class)
class EbayBrowseApiSourceTest {

    private static final ImdbId HEAT = ImdbId.of("tt0113277");
    private static final Instant NOW = Instant.parse("2026-09-05T14:00:00Z");

    private static final String TWO_FIXED_PRICE_ITEMS = """
            {"itemSummaries":[
              {"itemWebUrl":"https://www.ebay.de/itm/1",
               "price":{"value":"12.99","currency":"EUR"},
               "shippingOptions":[{"shippingCost":{"value":"3.99","currency":"EUR"}}]},
              {"itemWebUrl":"https://www.ebay.de/itm/2",
               "price":{"value":"13.50","currency":"EUR"},
               "shippingOptions":[{"shippingCost":{"value":"0.00","currency":"EUR"}}]}
            ]}""";

    @Mock
    private HttpClient httpClient;
    @Mock
    private HttpResponse<String> response;
    @Mock
    private EbayOAuthTokenProvider tokenProvider;
    @Mock
    private TimeService timeService;

    private static EbayProperties properties(boolean enabled) {
        return EbayPropertiesFixture.enabled(enabled);
    }

    private EbayBrowseApiSource source(boolean enabled) {
        return new EbayBrowseApiSource(properties(enabled), tokenProvider, () -> httpClient, timeService);
    }

    // --- URI construction -------------------------------------------------------------------

    @Test
    void theSearchUriCarriesTermCategoryFilterAndSort() {
        final var uri = EbayBrowseApiSource.searchUri("https://api.ebay.com", "Heat Blu-ray", "617", 3,
                BuyingOption.FIXED_PRICE);

        assertThat(uri.toString())
                .startsWith("https://api.ebay.com/buy/browse/v1/item_summary/search?")
                .contains("q=Heat%20Blu-ray")
                .contains("category_ids=617")
                .contains("filter=buyingOptions:%7BFIXED_PRICE%7D")
                .contains("sort=price")
                .contains("limit=3");
    }

    @Test
    void theAuctionFilterDiffersFromTheFixedPriceOne() {
        final var uri = EbayBrowseApiSource.searchUri("https://api.ebay.com", "Heat", "617", 3,
                BuyingOption.AUCTION);

        assertThat(uri.toString()).contains("filter=buyingOptions:%7BAUCTION%7D");
    }

    @Test
    void aSearchTermWithSpecialCharactersIsEncodedRatherThanBreakingTheUri() {
        final var uri = EbayBrowseApiSource.searchUri("https://api.ebay.com", "Am&Ende \"gut\"", "617", 3,
                BuyingOption.FIXED_PRICE);

        assertThat(uri.getQuery()).contains("Am&Ende \"gut\"");
        assertThat(uri.toString()).doesNotContain("\"");
    }

    // --- Response mapping -------------------------------------------------------------------

    @Test
    void theCheapestOfferIsChosenByTotalNotByItemPrice() {
        // Item 1 is cheaper on its own (12.99 < 13.50) but costs more once shipping is added.
        final var cheapest = EbayBrowseApiSource.cheapestOffer(TWO_FIXED_PRICE_ITEMS, Marketplace.EBAY_DE,
                BuyingOption.FIXED_PRICE);

        assertThat(cheapest).get()
                .extracting(Offer::price, Offer::shipping, Offer::total, Offer::url)
                .isEqualTo(List.of(OfferPrice.of(1350, "EUR"), Optional.of(OfferPrice.of(0, "EUR")),
                        OfferPrice.of(1350, "EUR"), URI.create("https://www.ebay.de/itm/2")));
    }

    @Test
    void anAuctionReadsTheCurrentBidInsteadOfThePriceField() {
        final var json = """
                {"itemSummaries":[{"itemWebUrl":"https://www.ebay.de/itm/9",
                 "currentBidPrice":{"value":"4.50","currency":"EUR"}}]}""";

        final var cheapest = EbayBrowseApiSource.cheapestOffer(json, Marketplace.EBAY_DE, BuyingOption.AUCTION);

        assertThat(cheapest).get()
                .extracting(Offer::price, Offer::shipping)
                .isEqualTo(List.of(OfferPrice.of(450, "EUR"), Optional.empty()));
    }

    @Test
    void aMissingShippingCostMeansUnstatedNotFree() {
        final var json = """
                {"itemSummaries":[{"itemWebUrl":"https://www.ebay.de/itm/1",
                 "price":{"value":"9.99","currency":"EUR"}}]}""";

        final var cheapest = EbayBrowseApiSource.cheapestOffer(json, Marketplace.EBAY_DE, BuyingOption.FIXED_PRICE);

        assertThat(cheapest).get()
                .extracting(Offer::shipping, Offer::total)
                .isEqualTo(List.of(Optional.empty(), OfferPrice.of(999, "EUR")));
    }

    @Test
    void anEmptyResultSetYieldsNoOffer() {
        assertThat(EbayBrowseApiSource.cheapestOffer("{\"itemSummaries\":[]}", Marketplace.EBAY_DE,
                BuyingOption.FIXED_PRICE)).isEmpty();
    }

    @Test
    void aResponseWithoutItemSummariesYieldsNoOffer() {
        assertThat(EbayBrowseApiSource.cheapestOffer("{\"total\":0}", Marketplace.EBAY_DE,
                BuyingOption.FIXED_PRICE)).isEmpty();
    }

    @Test
    void anItemLinkingOutsideTheMarketplaceIsSkippedRatherThanTrusted() {
        final var json = """
                {"itemSummaries":[
                  {"itemWebUrl":"https://evil.example.com/itm/1","price":{"value":"1.00","currency":"EUR"}},
                  {"itemWebUrl":"https://www.ebay.de/itm/2","price":{"value":"9.99","currency":"EUR"}}
                ]}""";

        final var cheapest = EbayBrowseApiSource.cheapestOffer(json, Marketplace.EBAY_DE, BuyingOption.FIXED_PRICE);

        // The cheaper entry is dropped by the host allowlist, so the legitimate one wins.
        assertThat(cheapest).get()
                .extracting(Offer::url)
                .isEqualTo(URI.create("https://www.ebay.de/itm/2"));
    }

    @Test
    void anItemPricedInAForeignCurrencyIsSkipped() {
        final var json = """
                {"itemSummaries":[
                  {"itemWebUrl":"https://www.ebay.de/itm/1","price":{"value":"1.00","currency":"CHF"}},
                  {"itemWebUrl":"https://www.ebay.de/itm/2","price":{"value":"9.99","currency":"EUR"}}
                ]}""";

        assertThat(EbayBrowseApiSource.cheapestOffer(json, Marketplace.EBAY_DE, BuyingOption.FIXED_PRICE)).get()
                .extracting(Offer::url)
                .isEqualTo(URI.create("https://www.ebay.de/itm/2"));
    }

    @Test
    void anItemWithAnUnreadableAmountIsSkippedRatherThanFailingTheLookup() {
        final var json = """
                {"itemSummaries":[
                  {"itemWebUrl":"https://www.ebay.de/itm/1","price":{"value":"twelve","currency":"EUR"}},
                  {"itemWebUrl":"https://www.ebay.de/itm/2","price":{"value":"9.99","currency":"EUR"}}
                ]}""";

        assertThat(EbayBrowseApiSource.cheapestOffer(json, Marketplace.EBAY_DE, BuyingOption.FIXED_PRICE)).get()
                .extracting(Offer::price)
                .isEqualTo(OfferPrice.of(999, "EUR"));
    }

    @Test
    void anUnreadableBodyIsAnUnavailableSourceNotAnEmptyResult() {
        assertThatExceptionOfType(OfferSourceUnavailableException.class)
                .isThrownBy(() -> EbayBrowseApiSource.cheapestOffer("<html>blocked</html>",
                        Marketplace.EBAY_DE, BuyingOption.FIXED_PRICE))
                .withMessageContaining("not readable JSON");
    }

    // --- End-to-end through the mocked client ------------------------------------------------

    @Test
    void bothBuyingOptionsAreQueriedAndCombinedIntoOneResult() throws Exception {
        when(tokenProvider.accessToken()).thenReturn("tok");
        when(timeService.now()).thenReturn(NOW);
        doReturn(response).when(httpClient).send(any(), any());
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(TWO_FIXED_PRICE_ITEMS, """
                {"itemSummaries":[{"itemWebUrl":"https://www.ebay.de/itm/9",
                 "currentBidPrice":{"value":"4.50","currency":"EUR"}}]}""");

        final var offers = source(true).findOffers(HEAT, "Heat Blu-ray", Marketplace.EBAY_DE);

        assertThat(offers)
                .isNotNull()
                .extracting(TitleOffers::imdbId, TitleOffers::fetchedAt, TitleOffers::hasAnyOffer)
                .isEqualTo(List.of(HEAT, NOW, true));
        assertThat(offers.buyNow()).get().extracting(Offer::url)
                .isEqualTo(URI.create("https://www.ebay.de/itm/2"));
        assertThat(offers.auction()).get().extracting(Offer::price)
                .isEqualTo(OfferPrice.of(450, "EUR"));
    }

    @Test
    void aLookupThatFindsNothingIsAnOrdinaryEmptyResult() throws Exception {
        when(tokenProvider.accessToken()).thenReturn("tok");
        when(timeService.now()).thenReturn(NOW);
        doReturn(response).when(httpClient).send(any(), any());
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"itemSummaries\":[]}");

        final var offers = source(true).findOffers(HEAT, "Heat Blu-ray", Marketplace.EBAY_DE);

        assertThat(offers)
                .isNotNull()
                .extracting(TitleOffers::buyNow, TitleOffers::auction, TitleOffers::hasAnyOffer)
                .isEqualTo(List.of(Optional.empty(), Optional.empty(), false));
    }

    @Test
    void aQuotaRejectionIsDistinguishedFromAnOrdinaryFailure() throws Exception {
        when(tokenProvider.accessToken()).thenReturn("tok");
        when(timeService.now()).thenReturn(NOW);
        doReturn(response).when(httpClient).send(any(), any());
        when(response.statusCode()).thenReturn(429);
        when(response.body()).thenReturn("{\"errors\":[{\"errorId\":2001}]}");
        when(response.headers()).thenReturn(HttpHeaders.of(
                java.util.Map.of("X-RateLimit-Reset", List.of("1234")), (k, v) -> true));

        assertThatExceptionOfType(UpstreamQuotaExhaustedException.class)
                .isThrownBy(() -> source(true).findOffers(HEAT, "Heat", Marketplace.EBAY_DE))
                .withMessageContaining("HTTP 429");
    }

    @Test
    void anOrdinaryRejectionIsNotMistakenForAQuotaOne() throws Exception {
        when(tokenProvider.accessToken()).thenReturn("tok");
        doReturn(response).when(httpClient).send(any(), any());
        when(response.statusCode()).thenReturn(500);
        when(response.body()).thenReturn("{\"errors\":[{\"errorId\":5000}]}");
        when(response.headers()).thenReturn(HttpHeaders.of(java.util.Map.of(), (k, v) -> true));

        assertThatExceptionOfType(OfferSourceUnavailableException.class)
                .isThrownBy(() -> source(true).findOffers(HEAT, "Heat", Marketplace.EBAY_DE))
                .withMessageContaining("HTTP 500");
        assertThat(EbayBrowseApiSource.looksLikeQuotaExhaustion(500, "{\"errors\":[{\"errorId\":5000}]}"))
                .isFalse();
    }

    @Test
    void bothDocumentedQuotaSignalsAreRecognised() {
        // Unverified on purpose (plan section 8) — this pins what we currently believe, so the
        // first real response can be checked against it.
        assertThat(EbayBrowseApiSource.looksLikeQuotaExhaustion(429, "{}")).isTrue();
        assertThat(EbayBrowseApiSource.looksLikeQuotaExhaustion(403, "{\"errors\":[{\"errorId\":2001}]}"))
                .isTrue();
        assertThat(EbayBrowseApiSource.looksLikeQuotaExhaustion(500, "{}")).isFalse();
        assertThat(EbayBrowseApiSource.looksLikeQuotaExhaustion(500, null)).isFalse();
    }

    @Test
    void aTransportFailureIsAnUnavailableSource() throws Exception {
        when(tokenProvider.accessToken()).thenReturn("tok");
        doThrow(new IOException("connection reset")).when(httpClient).send(any(), any());

        assertThatExceptionOfType(OfferSourceUnavailableException.class)
                .isThrownBy(() -> source(true).findOffers(HEAT, "Heat", Marketplace.EBAY_DE))
                .withCauseInstanceOf(IOException.class);
    }

    @Test
    void anUnconfiguredIntegrationFailsLoudlyInsteadOfSilentlyReturningNothing() {
        lenient().when(tokenProvider.accessToken()).thenReturn("tok");

        assertThatExceptionOfType(OfferSourceUnavailableException.class)
                .isThrownBy(() -> source(false).findOffers(HEAT, "Heat", Marketplace.EBAY_DE))
                .withMessageContaining("not configured");
    }
}
