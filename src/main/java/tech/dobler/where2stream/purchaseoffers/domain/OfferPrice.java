package tech.dobler.where2stream.purchaseoffers.domain;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * The price of a purchase offer: an amount in the currency's <em>minor</em> units, plus the
 * currency itself (ADR-0009 — a value object rather than a bare number, so an amount can't be
 * separated from its currency or confused with an unrelated {@code long}).
 *
 * <p>Minor units, not a decimal: money is counted, not measured, and a {@code double} would make
 * "cheapest offer" comparisons depend on rounding. For EUR and USD a minor unit is a cent, which
 * is why the API DTO calls the field {@code amountCents}; the domain avoids that name because the
 * number of minor units per major unit is a property of the currency (JPY has none, KWD has three)
 * and is read from {@link Currency#getDefaultFractionDigits()} rather than assumed to be two.
 *
 * <p>The amount may be zero — a running auction with no bid yet legitimately starts at 0 — but
 * never negative.
 */
public record OfferPrice(long minorUnits, Currency currency) implements Comparable<OfferPrice> {

    public OfferPrice {
        Objects.requireNonNull(currency, "currency must not be null");
        if (minorUnits < 0) {
            throw new IllegalArgumentException("Offer price must not be negative: " + minorUnits);
        }
    }

    public static OfferPrice of(long minorUnits, String currencyCode) {
        return new OfferPrice(minorUnits, currencyOf(currencyCode));
    }

    /**
     * Builds a price from an amount in <em>major</em> units — the form eBay reports
     * ({@code {"value": "12.34", "currency": "EUR"}}).
     *
     * @throws IllegalArgumentException if the amount carries more decimal places than the currency
     *                                  has minor units, since silently rounding a price we are
     *                                  about to show as "the cheapest" would be worse than failing
     */
    public static OfferPrice ofMajorUnits(BigDecimal amount, String currencyCode) {
        Objects.requireNonNull(amount, "amount must not be null");
        final var currency = currencyOf(currencyCode);
        final var fractionDigits = currency.getDefaultFractionDigits();
        if (fractionDigits < 0) {
            throw new IllegalArgumentException(
                    "Currency without minor units is not a usable price currency: " + currencyCode);
        }
        try {
            return new OfferPrice(amount.movePointRight(fractionDigits).longValueExact(), currency);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "Amount %s has more precision than %s allows".formatted(amount, currencyCode), e);
        }
    }

    private static Currency currencyOf(String currencyCode) {
        Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        return Currency.getInstance(currencyCode);
    }

    /**
     * Orders by amount, cheapest first.
     *
     * @throws IllegalArgumentException if the currencies differ — there is no exchange rate here,
     *                                  and comparing across currencies would quietly produce a
     *                                  wrong "cheapest offer"
     */
    @Override
    public int compareTo(OfferPrice other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot compare prices in different currencies: %s and %s"
                            .formatted(currency.getCurrencyCode(), other.currency.getCurrencyCode()));
        }
        return Long.compare(minorUnits, other.minorUnits);
    }
}
