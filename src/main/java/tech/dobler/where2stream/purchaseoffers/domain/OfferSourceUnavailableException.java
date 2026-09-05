package tech.dobler.where2stream.purchaseoffers.domain;

/**
 * The offer source could not be reached or refused to answer — a transport failure, a rejected
 * token, a rate-limit or quota response, a malformed payload.
 *
 * <p>This is deliberately distinct from "the lookup succeeded and there is nothing to buy", which
 * a {@link PurchaseOfferSource} reports as {@link TitleOffers#none} instead. The two must not
 * collapse into one another: the client shows them differently ("no offers found" vs. "currently
 * unavailable"), and only this one may trip the circuit breaker. If an unreachable source returned
 * an empty result, the application could not tell a broken integration from an unpopular film —
 * which is exactly the failure mode the hit-rate logging in the plan's section 5.5 exists to catch.
 *
 * <p>Unchecked, and <em>not</em> meant to escape into a user request: the application layer catches
 * it and degrades. It lives in {@code domain} for the same reason {@code ScrapingException} does —
 * so layers that must reference it may, without crossing the boundaries {@code ArchitectureTest}
 * enforces.
 */
public class OfferSourceUnavailableException extends RuntimeException {

    public OfferSourceUnavailableException(String message) {
        super(message);
    }

    public OfferSourceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
