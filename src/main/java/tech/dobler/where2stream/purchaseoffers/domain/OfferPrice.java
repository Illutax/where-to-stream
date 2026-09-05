package tech.dobler.where2stream.purchaseoffers.domain;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The price of a purchase offer: an amount in the currency's <em>minor</em> units, plus the
 * currency itself (ADR-0009 — a value object rather than a bare number, so an amount can't be
 * separated from its currency or confused with an unrelated {@code long}).
 *
 * <p>Minor units, not a decimal: money is counted, not measured, and a {@code double} would make
 * "cheapest offer" comparisons depend on rounding. The field is not called {@code amountCents}
 * (the name the API DTO uses) because a minor unit is only a cent for EUR and USD — GBP's is a
 * penny — and because the count per major unit is a property of the currency, read from
 * {@link Currency#getDefaultFractionDigits()} rather than assumed.
 *
 * <p><strong>Only EUR, USD and GBP are accepted.</strong> Those are the currencies of the
 * marketplaces this feature targets ({@code ebay.de}, {@code ebay.com}, {@code ebay.co.uk}), and
 * restricting the set turns an unexpected currency into an immediate, visible failure instead of a
 * price rendered with the wrong symbol or compared against an amount it has no exchange rate with.
 * Widening the set is a one-line change here plus the marketplace that needs it.
 *
 * <p>The amount may be zero — a running auction with no bid yet legitimately starts at 0 — but
 * never negative.
 */
public record OfferPrice(long minorUnits, Currency currency) implements Comparable<OfferPrice> {

    /** The marketplaces this feature targets settle in these three, and nothing else is expected. */
    private static final Set<Currency> SUPPORTED = Stream.of("EUR", "USD", "GBP")
            .map(Currency::getInstance)
            .collect(Collectors.toUnmodifiableSet());

    public OfferPrice {
        Objects.requireNonNull(currency, "currency must not be null");
        if (!SUPPORTED.contains(currency)) {
            throw new IllegalArgumentException(
                    "Unsupported offer currency %s — expected one of %s"
                            .formatted(currency.getCurrencyCode(), supportedCodes()));
        }
        if (minorUnits < 0) {
            throw new IllegalArgumentException("Offer price must not be negative: " + minorUnits);
        }
    }

    private static List<String> supportedCodes() {
        return SUPPORTED.stream().map(Currency::getCurrencyCode).sorted().toList();
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
        try {
            return new OfferPrice(
                    amount.movePointRight(currency.getDefaultFractionDigits()).longValueExact(), currency);
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
