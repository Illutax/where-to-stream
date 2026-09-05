package tech.dobler.where2stream.purchaseoffers.domain;

/**
 * eBay itself reported that the application's daily call allowance is spent.
 *
 * <p>A special case of {@link OfferSourceUnavailableException} because it is handled differently:
 * an ordinary failure trips the circuit breaker for a few minutes, this one closes the whole quota
 * day and is written to the database (ADR-0017).
 *
 * <p><strong>Detection is unverified.</strong> Which status and error code eBay actually uses is
 * one of the open points in the plan's section 8 — the developer account was still pending when
 * this was written. If the detection is wrong, the failure mode is benign: the day is not closed,
 * and the ordinary circuit breaker takes over instead.
 */
public class UpstreamQuotaExhaustedException extends OfferSourceUnavailableException {

    public UpstreamQuotaExhaustedException(String message) {
        super(message);
    }
}
