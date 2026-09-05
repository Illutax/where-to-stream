package tech.dobler.where2stream.purchaseoffers.domain;

import java.net.URI;
import java.util.Arrays;
import java.util.Currency;
import java.util.Locale;
import java.util.Optional;

/**
 * An eBay marketplace: which site is queried, in which currency, and which hosts its offer links
 * are allowed to point at.
 *
 * <p>Restricted to the three marketplaces whose currencies {@link OfferPrice} accepts. Each user
 * picks one in their settings (plan, decision 6.4), so two users may legitimately see different
 * prices for the same title — nothing is cached, so there is no shared value that could be wrong.
 *
 * <p>Lives in {@code purchaseoffers} rather than in {@code accountaccess}, even though the user's
 * choice is stored there: a marketplace is an eBay concept, not an account concept. Keeping it here
 * means {@code accountaccess} does not carry an eBay type in its published contract, and no
 * ArchUnit exemption is needed for it (contrast {@code ImdbEntry}/{@code WatchlistDate}, which did
 * need one).
 */
public enum Marketplace {

    EBAY_DE("EBAY_DE", "EUR", "ebay.de"),
    EBAY_US("EBAY_US", "USD", "ebay.com"),
    EBAY_GB("EBAY_GB", "GBP", "ebay.co.uk");

    private final String marketplaceId;
    private final Currency currency;
    private final String baseDomain;

    Marketplace(String marketplaceId, String currencyCode, String baseDomain) {
        this.marketplaceId = marketplaceId;
        this.currency = Currency.getInstance(currencyCode);
        this.baseDomain = baseDomain;
    }

    /** The value eBay expects in the {@code X-EBAY-C-MARKETPLACE-ID} request header. */
    public String marketplaceId() {
        return marketplaceId;
    }

    /**
     * Resolves a stored marketplace id, or empty if it names nothing we know.
     *
     * <p>Returns an {@link Optional} rather than throwing, and that is not defensiveness for its own
     * sake: the value comes from a {@code varchar} column in another context, written at a time when
     * this enum may have looked different. Removing a marketplace here would leave rows pointing at
     * it, and a lookup blowing up on someone's stale preference is a worse outcome than quietly
     * falling back to the default.
     */
    public static Optional<Marketplace> byId(String marketplaceId) {
        return Arrays.stream(values())
                .filter(marketplace -> marketplace.marketplaceId.equals(marketplaceId))
                .findFirst();
    }

    /** The currency offers on this marketplace are priced in. */
    public Currency currency() {
        return currency;
    }

    public String baseDomain() {
        return baseDomain;
    }

    /**
     * The host allowlist from the plan's section 5.3: whether an offer URL points at this
     * marketplace.
     *
     * <p>The URL arrives inside an eBay response and ends up in an {@code <a href>}, so it is
     * input, not output. Accepts the base domain itself and any subdomain of it
     * ({@code ebay.de}, {@code www.ebay.de}), and nothing else — note that the leading dot in the
     * suffix check is what stops {@code notebay.de} and {@code ebay.de.example.com} from passing.
     */
    public boolean allowsHostOf(URI url) {
        final var host = url.getHost();
        if (host == null) {
            return false;
        }
        final var normalised = host.toLowerCase(Locale.ROOT);
        return normalised.equals(baseDomain) || normalised.endsWith("." + baseDomain);
    }
}
