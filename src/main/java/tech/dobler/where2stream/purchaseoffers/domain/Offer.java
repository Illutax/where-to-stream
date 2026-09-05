package tech.dobler.where2stream.purchaseoffers.domain;

import java.net.URI;
import java.util.Objects;

/**
 * A single purchase offer: what it costs and where to buy it.
 *
 * <p>The URL originates in a response from eBay and is therefore <em>input</em>, not output — it
 * is rendered as an {@code <a href>} in the client, so a malformed or hostile value must not get
 * that far. Two invariants are enforced here because they hold regardless of configuration:
 * the URL is absolute, and its scheme is {@code https}. That alone rules out {@code javascript:}
 * and other scheme-based tricks.
 *
 * <p>Deliberately <em>not</em> enforced here: the eBay host allowlist from the plan's section 5.3.
 * Which hosts are acceptable ({@code ebay.de}, {@code ebay.com}, …) depends on the configured
 * marketplace, and configuration has no place in a domain record — that check belongs to the
 * adapter that knows the configuration.
 */
public record Offer(OfferPrice price, URI url) {

    public Offer {
        Objects.requireNonNull(price, "price must not be null");
        Objects.requireNonNull(url, "url must not be null");
        if (!url.isAbsolute()) {
            throw new IllegalArgumentException("Offer URL must be absolute: " + url);
        }
        if (!"https".equalsIgnoreCase(url.getScheme())) {
            throw new IllegalArgumentException("Offer URL must use https: " + url);
        }
    }
}
