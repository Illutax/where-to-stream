package tech.dobler.where2stream.purchaseoffers.port.out;

import tech.dobler.where2stream.purchaseoffers.domain.OfferSourceUnavailableException;
import tech.dobler.where2stream.purchaseoffers.domain.TitleOffers;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;

/**
 * Where purchase offers for a title come from.
 *
 * <p>This is the seam the whole feature is built around. The plan (section 4.2) picks the eBay
 * Browse API as the source, after a proof of concept showed that scraping the search results page
 * gets the server's IP blocked within minutes. That choice is expected to be revisited — either
 * because the quota turns out to be too small, or because the terms change — so nothing above this
 * interface knows how offers are obtained. Application layer, API endpoint, DTO and the entire
 * frontend are unaffected by swapping the implementation; the cost of changing course is one
 * adapter class, not the feature.
 *
 * <p><strong>Contract.</strong> An implementation reports three outcomes, and the difference
 * between the last two matters:
 * <ul>
 *   <li>offers found → a {@link TitleOffers} carrying whichever of the two offer kinds exist;</li>
 *   <li>nothing to buy → {@link TitleOffers#none} — an ordinary, successful result;</li>
 *   <li>source unreachable or refusing → {@link OfferSourceUnavailableException}.</li>
 * </ul>
 * An implementation must not signal unavailability by returning an empty result. Doing so would
 * make a broken integration indistinguishable from a title nobody sells, would leave the circuit
 * breaker blind, and would show the user "no offers" when the truth is "we could not ask".
 *
 * <p>Implementations are expected to apply their own outbound throttling and to keep any
 * credentials to themselves; neither is visible through this interface.
 */
public interface PurchaseOfferSource {

    /**
     * Looks up the cheapest fixed-price offer and the cheapest running auction for a title.
     *
     * @param imdbId     identifies the title, and is carried through into the result
     * @param searchTerm what to search the marketplace for.
     *                   Built by the application layer from data we already hold — never taken
     *                   from the request — so this endpoint cannot be used as an open search proxy
     *                   (plan section 5.1)
     * @return the offers found, or {@link TitleOffers#none} if the lookup succeeded but found none
     * @throws OfferSourceUnavailableException if the source could not be reached or refused to
     *                                         answer
     */
    TitleOffers findOffers(ImdbId imdbId, String searchTerm);
}
