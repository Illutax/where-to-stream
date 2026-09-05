package tech.dobler.where2stream.purchaseoffers.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class OfferPriceTest {

    private static final Currency EUR = Currency.getInstance("EUR");

    @Test
    void ofMajorUnitsScalesByTheCurrencysFractionDigits() {
        final var price = OfferPrice.ofMajorUnits(new BigDecimal("12.34"), "EUR");

        assertThat(price)
                .isNotNull()
                .extracting(OfferPrice::minorUnits, OfferPrice::currency)
                .isEqualTo(List.of(1234L, EUR));
    }

    @Test
    void ofMajorUnitsHandlesACurrencyWithoutMinorUnits() {
        final var price = OfferPrice.ofMajorUnits(new BigDecimal("1500"), "JPY");

        assertThat(price)
                .isNotNull()
                .extracting(OfferPrice::minorUnits, OfferPrice::currency)
                .isEqualTo(List.of(1500L, Currency.getInstance("JPY")));
    }

    @Test
    void ofMajorUnitsKeepsTrailingZeroesFromReachingTheAmount() {
        final var price = OfferPrice.ofMajorUnits(new BigDecimal("9.90"), "EUR");

        assertThat(price.minorUnits()).isEqualTo(990L);
    }

    @Test
    void ofMajorUnitsRejectsMorePrecisionThanTheCurrencyAllows() {
        final var amount = new BigDecimal("12.345");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> OfferPrice.ofMajorUnits(amount, "EUR"))
                .withMessageContaining("more precision");
    }

    @Test
    void ofMajorUnitsRejectsAnUnknownCurrencyCode() {
        final var amount = new BigDecimal("1.00");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> OfferPrice.ofMajorUnits(amount, "NOPE"));
    }

    @Test
    void ofMajorUnitsRejectsANullAmount() {
        assertThatNullPointerException()
                .isThrownBy(() -> OfferPrice.ofMajorUnits(null, "EUR"));
    }

    @Test
    void aZeroAmountIsAllowedBecauseAnAuctionCanStartAtZero() {
        assertThat(OfferPrice.of(0, "EUR").minorUnits()).isZero();
    }

    @Test
    void aNegativeAmountIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OfferPrice.of(-1, "EUR"))
                .withMessageContaining("must not be negative");
    }

    @Test
    void pricesInTheSameCurrencyAreOrderedCheapestFirst() {
        final var cheap = OfferPrice.of(500, "EUR");
        final var expensive = OfferPrice.of(1200, "EUR");

        assertThat(List.of(expensive, cheap).stream().sorted().toList())
                .containsExactly(cheap, expensive);
    }

    @Test
    void comparingPricesInDifferentCurrenciesIsRejectedRatherThanGuessed() {
        final var euros = OfferPrice.of(500, "EUR");
        final var dollars = OfferPrice.of(500, "USD");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> euros.compareTo(dollars))
                .withMessageContaining("different currencies");
    }
}
