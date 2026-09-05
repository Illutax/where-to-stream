package tech.dobler.where2stream.purchaseoffers.domain;

/**
 * The outcome of asking for permission to spend calls from the daily budget.
 *
 * <p>The two refusals are kept apart all the way to the client, because the difference matters to
 * the person reading it: "you have used up your share for today" is about them and their own
 * behaviour, "the shared budget is gone" is not. Collapsing both into a generic "unavailable"
 * would leave a user unable to tell whether waiting, or doing less, is the answer.
 */
public enum QuotaVerdict {

    ALLOWED,

    /** This user's own daily allowance is spent; other users may still have theirs. */
    USER_ALLOWANCE_REACHED,

    /** The application-wide allowance is spent — either counted out or declared so by eBay. */
    GLOBAL_BUDGET_EXHAUSTED
}
