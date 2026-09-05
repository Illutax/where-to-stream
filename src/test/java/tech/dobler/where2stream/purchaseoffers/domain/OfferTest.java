package tech.dobler.where2stream.purchaseoffers.domain;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class OfferTest {

    private static final OfferPrice PRICE = OfferPrice.of(1299, "EUR");

    @Test
    void anHttpsOfferUrlIsAccepted() {
        final var url = URI.create("https://www.ebay.de/itm/123456");

        final var offer = Offer.withoutStatedShipping(PRICE, url);

        assertThat(offer)
                .isNotNull()
                .extracting(Offer::price, Offer::shipping, Offer::url)
                .isEqualTo(List.of(PRICE, Optional.empty(), url));
    }

    @Test
    void aRelativeUrlIsRejected() {
        final var url = URI.create("/itm/123456");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Offer.withoutStatedShipping(PRICE, url))
                .withMessageContaining("absolute");
    }

    @Test
    void aPlainHttpUrlIsRejected() {
        final var url = URI.create("http://www.ebay.de/itm/123456");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Offer.withoutStatedShipping(PRICE, url))
                .withMessageContaining("https");
    }

    @Test
    void aJavascriptUrlIsRejected() {
        final var url = URI.create("javascript:alert(1)");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Offer.withoutStatedShipping(PRICE, url))
                .withMessageContaining("https");
    }

    @Test
    void aNullPriceIsRejected() {
        final var url = URI.create("https://www.ebay.de/itm/123456");

        assertThatNullPointerException()
                .isThrownBy(() -> Offer.withoutStatedShipping(null, url));
    }

    @Test
    void aNullUrlIsRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> Offer.withoutStatedShipping(PRICE, null));
    }

    @Test
    void aStatedShippingCostIsCarriedSeparatelyAndAddedForTheTotal() {
        final var url = URI.create("https://www.ebay.de/itm/123456");
        final var shipping = OfferPrice.of(399, "EUR");

        final var offer = new Offer(PRICE, Optional.of(shipping), url);

        assertThat(offer)
                .isNotNull()
                .extracting(Offer::price, Offer::shipping, Offer::total)
                .isEqualTo(List.of(PRICE, Optional.of(shipping), OfferPrice.of(1698, "EUR")));
    }

    @Test
    void unstatedShippingIsNotTreatedAsFree() {
        final var offer = Offer.withoutStatedShipping(PRICE, URI.create("https://www.ebay.de/itm/1"));

        // The total falls back to the item price, but shipping stays absent rather than becoming 0 —
        // the client has to be able to tell "no shipping cost" from "shipping cost unknown".
        assertThat(offer)
                .isNotNull()
                .extracting(Offer::shipping, Offer::total)
                .isEqualTo(List.of(Optional.empty(), PRICE));
    }

    @Test
    void shippingInADifferentCurrencyThanTheItemIsRejected() {
        final var url = URI.create("https://www.ebay.de/itm/123456");
        final var shippingInDollars = Optional.of(OfferPrice.of(399, "USD"));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Offer(PRICE, shippingInDollars, url))
                .withMessageContaining("does not match item currency");
    }

    @Test
    void aNullShippingOptionalIsRejected() {
        final var url = URI.create("https://www.ebay.de/itm/123456");

        assertThatNullPointerException()
                .isThrownBy(() -> new Offer(PRICE, null, url))
                .withMessageContaining("Optional.empty()");
    }
}
