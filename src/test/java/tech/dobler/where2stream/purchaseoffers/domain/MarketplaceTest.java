package tech.dobler.where2stream.purchaseoffers.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class MarketplaceTest {

    @Test
    void everyMarketplaceCarriesItsEbayIdAndCurrency() {
        assertThat(Marketplace.values())
                .extracting(Marketplace::marketplaceId, Marketplace::currency, Marketplace::baseDomain)
                .containsExactly(
                        tuple("EBAY_DE", Currency.getInstance("EUR"), "ebay.de"),
                        tuple("EBAY_US", Currency.getInstance("USD"), "ebay.com"),
                        tuple("EBAY_GB", Currency.getInstance("GBP"), "ebay.co.uk"));
    }

    @Test
    void everyMarketplaceCurrencyIsOneOfferPriceAccepts() {
        assertThat(Marketplace.values())
                .allSatisfy(marketplace ->
                        assertThat(OfferPrice.of(100, marketplace.currency().getCurrencyCode()).currency())
                                .isEqualTo(marketplace.currency()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://ebay.de/itm/1",
            "https://www.ebay.de/itm/1",
            "https://cgi.ebay.de/itm/1",
            "https://WWW.EBAY.DE/itm/1"
    })
    void theMarketplacesOwnHostsAreAllowed(String url) {
        assertThat(Marketplace.EBAY_DE.allowsHostOf(URI.create(url))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://ebay.com/itm/1",          // a different marketplace
            "https://notebay.de/itm/1",        // suffix without the separating dot
            "https://ebay.de.example.com/itm/1", // base domain as a prefix of someone else's host
            "https://example.com/ebay.de"      // base domain only in the path
    })
    void everythingElseIsRejected(String url) {
        assertThat(Marketplace.EBAY_DE.allowsHostOf(URI.create(url))).isFalse();
    }

    @Test
    void aUriWithoutAHostIsRejectedRatherThanThrowing() {
        assertThat(Marketplace.EBAY_DE.allowsHostOf(URI.create("/itm/1"))).isFalse();
    }

    @Test
    void aStoredIdResolvesBackToItsMarketplace() {
        assertThat(Marketplace.byId("EBAY_GB")).contains(Marketplace.EBAY_GB);
    }

    @Test
    void everyMarketplaceIdRoundTrips() {
        assertThat(Marketplace.values())
                .allSatisfy(m -> assertThat(Marketplace.byId(m.marketplaceId())).contains(m));
    }

    @Test
    void anUnknownIdResolvesToNothingInsteadOfThrowing() {
        // The value arrives from a varchar column in another context, possibly written when this
        // enum looked different. Blowing up on a stale preference would be worse than falling back.
        assertThat(Marketplace.byId("EBAY_MARS")).isEmpty();
        assertThat(Marketplace.byId("")).isEmpty();
        assertThat(Marketplace.byId(null)).isEmpty();
    }
}
