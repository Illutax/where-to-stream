package tech.dobler.where2stream.purchaseoffers.domain;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * A single purchase offer: what the item costs, what shipping adds, and where to buy it.
 *
 * <p>Item price and shipping are kept apart rather than pre-summed, because the plan
 * (decision 6.3) shows both: two offers at 12,99 € are not equally cheap when one adds 2 € and the
 * other 8 € of shipping, and a single combined number would hide exactly that. {@link #total()}
 * produces the sum where one is wanted — for instance to decide which of two offers is the cheaper
 * one.
 *
 * <p>Shipping is {@link Optional} because it is genuinely unknown for some listings (collection
 * only, or a seller who does not state it up front). Absent means "not stated", <em>not</em>
 * "free" — the two must not be conflated, so {@link #total()} falls back to the item price alone
 * and the client is expected to say that shipping is unknown rather than imply it costs nothing.
 *
 * <p>The URL originates in a response from eBay and is therefore <em>input</em>, not output — it
 * is rendered as an {@code <a href>} in the client, so a malformed or hostile value must not get
 * that far. Two invariants are enforced here because they hold regardless of configuration:
 * the URL is absolute, and its scheme is {@code https}. That alone rules out {@code javascript:}
 * and other scheme-based tricks. The host allowlist is <em>not</em> here: which hosts are
 * acceptable depends on the marketplace, so that check lives on
 * {@link Marketplace#allowsHostOf(URI)} and is applied by the adapter that knows which marketplace
 * was queried.
 */
public record Offer(OfferPrice price, Optional<OfferPrice> shipping, URI url) {

    public Offer {
        Objects.requireNonNull(price, "price must not be null");
        Objects.requireNonNull(shipping, "shipping must not be null — use Optional.empty()");
        Objects.requireNonNull(url, "url must not be null");
        if (!url.isAbsolute()) {
            throw new IllegalArgumentException("Offer URL must be absolute: " + url);
        }
        if (!"https".equalsIgnoreCase(url.getScheme())) {
            throw new IllegalArgumentException("Offer URL must use https: " + url);
        }
        shipping.ifPresent(cost -> {
            if (!cost.currency().equals(price.currency())) {
                throw new IllegalArgumentException(
                        "Shipping currency %s does not match item currency %s"
                                .formatted(cost.currency().getCurrencyCode(), price.currency().getCurrencyCode()));
            }
        });
    }

    /** An offer whose shipping cost the marketplace did not state. */
    public static Offer withoutStatedShipping(OfferPrice price, URI url) {
        return new Offer(price, Optional.empty(), url);
    }

    /**
     * Item price plus shipping where shipping is known, item price alone otherwise.
     * Never silently treats unstated shipping as free — it simply cannot be included.
     */
    public OfferPrice total() {
        return shipping.map(price::plus).orElse(price);
    }
}
