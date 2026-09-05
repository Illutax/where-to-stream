package tech.dobler.where2stream.accountaccess.port.spi;

import java.util.Set;

/**
 * Which marketplace ids a user may choose from — supplied by the context that owns them.
 *
 * <p>Account &amp; Access stores the user's marketplace as a plain string, because the set of
 * marketplaces is Purchase Offers' domain and importing its enum would be one context reaching into
 * another (ADR-0014). But a stored string that nobody checks is a free-text field: nothing would
 * stop {@code "banana"} from being persisted and then silently falling back to a default on every
 * lookup, with the user's setting visibly not taking effect and no error anywhere.
 *
 * <p>So the check is declared here and answered there. This is the same inversion as
 * {@link PosterAttributionProvider} (ADR-0019): the context with the need states it, the context
 * with the knowledge satisfies it, and the arrow points from Purchase Offers to Account &amp;
 * Access — the direction that already exists.
 */
public interface SupportedMarketplaces {

    /** Every acceptable marketplace id, for validation and for telling the user what is allowed. */
    Set<String> ids();

    default boolean supports(String marketplaceId) {
        return marketplaceId != null && ids().contains(marketplaceId);
    }
}
