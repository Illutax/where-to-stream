package tech.dobler.where2stream.purchaseoffers.domain;

import org.junit.jupiter.api.Test;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TitleOffersTest {

    private static final ImdbId HEAT = ImdbId.of("tt0113277");
    private static final Instant FETCHED_AT = Instant.parse("2026-09-05T14:00:00Z");
    private static final Offer BUY_NOW =
            new Offer(OfferPrice.of(1299, "EUR"), URI.create("https://www.ebay.de/itm/1"));
    private static final Offer AUCTION =
            new Offer(OfferPrice.of(450, "EUR"), URI.create("https://www.ebay.de/itm/2"));

    @Test
    void bothOffersAreCarriedAlongWithTheFetchTimestamp() {
        final var offers = new TitleOffers(HEAT, Optional.of(BUY_NOW), Optional.of(AUCTION), FETCHED_AT);

        assertThat(offers)
                .isNotNull()
                .extracting(TitleOffers::imdbId, TitleOffers::buyNow, TitleOffers::auction, TitleOffers::fetchedAt)
                .isEqualTo(List.of(HEAT, Optional.of(BUY_NOW), Optional.of(AUCTION), FETCHED_AT));
    }

    @Test
    void theTwoOfferKindsAreIndependentlyAbsent() {
        final var auctionOnly = new TitleOffers(HEAT, Optional.empty(), Optional.of(AUCTION), FETCHED_AT);

        assertThat(auctionOnly)
                .isNotNull()
                .extracting(TitleOffers::buyNow, TitleOffers::auction, TitleOffers::hasAnyOffer)
                .isEqualTo(List.of(Optional.empty(), Optional.of(AUCTION), true));
    }

    @Test
    void noneIsACompletedLookupWithoutAnyOffer() {
        final var nothing = TitleOffers.none(HEAT, FETCHED_AT);

        assertThat(nothing)
                .isNotNull()
                .extracting(TitleOffers::imdbId, TitleOffers::buyNow, TitleOffers::auction,
                        TitleOffers::fetchedAt, TitleOffers::hasAnyOffer)
                .isEqualTo(List.of(HEAT, Optional.empty(), Optional.empty(), FETCHED_AT, false));
    }

    @Test
    void aNullOptionalIsRejectedSoAbsenceIsAlwaysExpressedAsEmpty() {
        assertThatNullPointerException()
                .isThrownBy(() -> new TitleOffers(HEAT, null, Optional.empty(), FETCHED_AT))
                .withMessageContaining("Optional.empty()");
    }

    @Test
    void aNullFetchTimestampIsRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> new TitleOffers(HEAT, Optional.empty(), Optional.empty(), null));
    }
}
