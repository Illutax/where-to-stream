package tech.dobler.where2stream.purchaseoffers.application.dto;

import tech.dobler.where2stream.purchaseoffers.domain.Offer;

/**
 * One offer, reduced to what the client actually renders.
 *
 * <p>Deliberately narrow (plan, section 5.3): no listing title, no seller name, no description, no
 * image URL. Every one of those is user-generated content from a third party, and the smallest way
 * to handle it safely is not to carry it at all. It is also the more robust choice — a field that
 * is never parsed cannot break when eBay changes it.
 *
 * @param amountCents   item price in the currency's minor units. Named for the wire contract; for
 *                      GBP these are pence, which is why the domain type avoids the name
 * @param shippingCents shipping cost, or {@code null} when the listing does not state one.
 *                      {@code null} means <em>unknown</em>, never <em>free</em> — the client has to
 *                      render those differently
 * @param currency      ISO code; always the queried marketplace's currency
 * @param url           the offer on eBay, already checked against the marketplace's host allowlist
 */
public record OfferDto(long amountCents, Long shippingCents, String currency, String url) {

    public static OfferDto from(Offer offer) {
        return new OfferDto(
                offer.price().minorUnits(),
                offer.shipping().map(cost -> (Long) cost.minorUnits()).orElse(null),
                offer.price().currency().getCurrencyCode(),
                offer.url().toString());
    }
}
