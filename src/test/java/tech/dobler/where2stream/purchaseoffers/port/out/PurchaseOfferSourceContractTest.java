package tech.dobler.where2stream.purchaseoffers.port.out;

import org.junit.jupiter.api.Test;
import tech.dobler.where2stream.purchaseoffers.domain.Offer;
import tech.dobler.where2stream.purchaseoffers.domain.OfferPrice;
import tech.dobler.where2stream.purchaseoffers.domain.OfferSourceUnavailableException;
import tech.dobler.where2stream.purchaseoffers.domain.TitleOffers;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Pins the three outcomes {@link PurchaseOfferSource} implementations must be able to express,
 * using a stub in place of a real adapter.
 * There is no production behaviour to exercise here yet — the value is that "nothing found" and
 * "source unavailable" stay two distinguishable things, which is the one contract detail an
 * implementation could plausibly get wrong.
 */
class PurchaseOfferSourceContractTest {

    private static final ImdbId HEAT = ImdbId.of("tt0113277");
    private static final Instant FETCHED_AT = Instant.parse("2026-09-05T14:00:00Z");

    @Test
    void aSourceCanReportOffersItFound() {
        final var buyNow = new Offer(OfferPrice.of(1299, "EUR"), URI.create("https://www.ebay.de/itm/1"));
        final PurchaseOfferSource source =
                (imdbId, term) -> new TitleOffers(imdbId, Optional.of(buyNow), Optional.empty(), FETCHED_AT);

        final var offers = source.findOffers(HEAT, "Heat Blu-ray");

        assertThat(offers)
                .isNotNull()
                .extracting(TitleOffers::imdbId, TitleOffers::buyNow, TitleOffers::hasAnyOffer)
                .isEqualTo(List.of(HEAT, Optional.of(buyNow), true));
    }

    @Test
    void aSuccessfulLookupWithoutResultsIsAnOrdinaryValueNotAnException() {
        final PurchaseOfferSource source = (imdbId, term) -> TitleOffers.none(imdbId, FETCHED_AT);

        final var offers = source.findOffers(HEAT, "Heat Blu-ray");

        assertThat(offers)
                .isNotNull()
                .extracting(TitleOffers::imdbId, TitleOffers::hasAnyOffer)
                .isEqualTo(List.of(HEAT, false));
    }

    @Test
    void anUnreachableSourceThrowsRatherThanReturningAnEmptyResult() {
        final PurchaseOfferSource source = (imdbId, term) -> {
            throw new OfferSourceUnavailableException("upstream refused", new IOException("connection reset"));
        };

        assertThatExceptionOfType(OfferSourceUnavailableException.class)
                .isThrownBy(() -> source.findOffers(HEAT, "Heat Blu-ray"))
                .withMessage("upstream refused")
                .withCauseInstanceOf(IOException.class);
    }

    @Test
    void theUnavailableExceptionAlsoWorksWithoutACause() {
        final var exception = new OfferSourceUnavailableException("daily quota exhausted");

        assertThat(exception)
                .isNotNull()
                .extracting(Throwable::getMessage, Throwable::getCause)
                .isEqualTo(java.util.Arrays.asList("daily quota exhausted", null));
    }
}
