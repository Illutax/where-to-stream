package tech.dobler.where2stream.purchaseoffers.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * What a price lookup produced, including the ways it can fail without being an error.
 *
 * <p>The four outcomes are kept distinct all the way to the client because they call for different
 * things from the reader: retry, wait until tomorrow, do less, or nothing at all. Collapsing them
 * into "unavailable" would leave a user unable to tell which.
 */
public record OfferLookupResult(Status status, Optional<TitleOffers> offers) {

    public enum Status {
        /** The lookup ran. The offers may still be empty — that is a result, not a failure. */
        FETCHED,
        /** eBay could not be asked: unreachable, refusing, or the circuit breaker is open. */
        UNAVAILABLE,
        /** This user has spent their own share of the daily budget. */
        USER_ALLOWANCE_REACHED,
        /** The application-wide daily budget is gone, for everyone. */
        GLOBAL_BUDGET_EXHAUSTED,
        /**
         * An admin is currently acting as this user, and lookups are suspended for the duration
         * (ADR-0020). Booking two calls against the impersonated user's allowance would spend
         * someone else's budget; booking them against the admin would require the quota layer to
         * know an identity ADR-0007 keeps from it. So neither happens.
         */
        IMPERSONATION_ACTIVE
    }

    public OfferLookupResult {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(offers, "offers must not be null — use Optional.empty()");
        if (status == Status.FETCHED && offers.isEmpty()) {
            throw new IllegalArgumentException("A FETCHED result must carry its offers");
        }
        if (status != Status.FETCHED && offers.isPresent()) {
            throw new IllegalArgumentException("Only a FETCHED result may carry offers");
        }
    }

    public static OfferLookupResult fetched(TitleOffers offers) {
        return new OfferLookupResult(Status.FETCHED, Optional.of(offers));
    }

    public static OfferLookupResult unavailable() {
        return new OfferLookupResult(Status.UNAVAILABLE, Optional.empty());
    }

    public static OfferLookupResult impersonationActive() {
        return new OfferLookupResult(Status.IMPERSONATION_ACTIVE, Optional.empty());
    }

    public static OfferLookupResult of(QuotaVerdict refusal) {
        return switch (refusal) {
            case USER_ALLOWANCE_REACHED -> new OfferLookupResult(Status.USER_ALLOWANCE_REACHED, Optional.empty());
            case GLOBAL_BUDGET_EXHAUSTED -> new OfferLookupResult(Status.GLOBAL_BUDGET_EXHAUSTED, Optional.empty());
            case ALLOWED -> throw new IllegalArgumentException("ALLOWED is not a refusal");
        };
    }
}
