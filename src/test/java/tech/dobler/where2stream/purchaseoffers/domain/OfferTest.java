package tech.dobler.where2stream.purchaseoffers.domain;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class OfferTest {

    private static final OfferPrice PRICE = OfferPrice.of(1299, "EUR");

    @Test
    void anHttpsOfferUrlIsAccepted() {
        final var url = URI.create("https://www.ebay.de/itm/123456");

        final var offer = new Offer(PRICE, url);

        assertThat(offer)
                .isNotNull()
                .extracting(Offer::price, Offer::url)
                .isEqualTo(List.of(PRICE, url));
    }

    @Test
    void aRelativeUrlIsRejected() {
        final var url = URI.create("/itm/123456");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Offer(PRICE, url))
                .withMessageContaining("absolute");
    }

    @Test
    void aPlainHttpUrlIsRejected() {
        final var url = URI.create("http://www.ebay.de/itm/123456");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Offer(PRICE, url))
                .withMessageContaining("https");
    }

    @Test
    void aJavascriptUrlIsRejected() {
        final var url = URI.create("javascript:alert(1)");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Offer(PRICE, url))
                .withMessageContaining("https");
    }

    @Test
    void aNullPriceIsRejected() {
        final var url = URI.create("https://www.ebay.de/itm/123456");

        assertThatNullPointerException()
                .isThrownBy(() -> new Offer(null, url));
    }

    @Test
    void aNullUrlIsRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Offer(PRICE, null));
    }
}
