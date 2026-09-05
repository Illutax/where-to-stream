package tech.dobler.where2stream.purchaseoffers.domain;

import tech.dobler.where2stream.shared.kernel.domain.ImdbId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * What a title currently costs on eBay: the cheapest fixed-price offer and the cheapest running
 * auction, as of {@link #fetchedAt}.
 *
 * <p>Both are {@link Optional} rather than nullable, and both are independently absent: a title
 * may have fixed-price listings but no running auction, or neither. "No offer" is an ordinary
 * result here, not an error — a lookup that finds nothing is indistinguishable, at this level,
 * from a title nobody is selling.
 *
 * <p>{@link #fetchedAt} is part of the value because these prices go stale in minutes: a bid can
 * change by the time the page renders, and a single-item fixed-price listing disappears entirely
 * once sold. The client shows it as an "as of …" timestamp so a displayed price never looks more
 * authoritative than it is.
 */
public record TitleOffers(ImdbId imdbId, Optional<Offer> buyNow, Optional<Offer> auction, Instant fetchedAt) {

    public TitleOffers {
        Objects.requireNonNull(imdbId, "imdbId must not be null");
        Objects.requireNonNull(buyNow, "buyNow must not be null — use Optional.empty()");
        Objects.requireNonNull(auction, "auction must not be null — use Optional.empty()");
        Objects.requireNonNull(fetchedAt, "fetchedAt must not be null");
    }

    /** A lookup that completed but found nothing to buy — a result, not a failure. */
    public static TitleOffers none(ImdbId imdbId, Instant fetchedAt) {
        return new TitleOffers(imdbId, Optional.empty(), Optional.empty(), fetchedAt);
    }

    /**
     * Whether this lookup produced at least one price.
     *
     * <p>Exists for the hit-rate logging the plan requires (section 5.5): if a change on eBay's
     * side breaks the response mapping, every lookup silently returns nothing — which looks exactly
     * like "there really are no offers" unless the ratio is measured.
     */
    public boolean hasAnyOffer() {
        return buyNow.isPresent() || auction.isPresent();
    }
}
