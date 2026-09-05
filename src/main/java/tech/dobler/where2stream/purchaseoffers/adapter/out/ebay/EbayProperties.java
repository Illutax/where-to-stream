package tech.dobler.where2stream.purchaseoffers.adapter.out.ebay;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import tech.dobler.where2stream.purchaseoffers.domain.Marketplace;

import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Binding for the {@code ebay.*} configuration (purchase offers via the eBay Browse API).
 *
 * <p>Like TMDB, this is <strong>opt-in</strong>: {@link #enabled()} defaults to {@code false} and
 * the integration additionally needs both credentials, so the application runs unchanged without
 * an eBay developer account. {@link #active()} is the single check for "can we actually call eBay".
 *
 * <p><strong>{@code toString()} is overridden on purpose.</strong> A record's generated
 * {@code toString()} prints every component, which would put {@code clientSecret} into any log line
 * that happens to include this object — a Spring startup binding failure prints the bound record,
 * for instance. Both credentials are therefore reduced to whether they are set. This is the
 * requirement from the plan's section 5.4, and it is the kind of thing that is invisible until it
 * has already leaked.
 *
 * @param clientId          OAuth client id from the eBay developer account
 * @param clientSecret      OAuth client secret; never logged, never sent to the browser
 * @param apiBaseUrl        eBay API root, used for both the OAuth and the Browse endpoints
 * @param defaultMarketplace marketplace used when a user has expressed no preference
 * @param categoryId        marketplace category the search is restricted to (DVDs &amp; Blu-ray).
 *                          Without it "Heat" mostly returns heating supplies — the plan calls the
 *                          hit quality the real product risk of this feature
 * @param resultsPerQuery   how many item summaries to ask for per call; the cheapest is taken from
 *                          the top of a price-sorted list, so this stays small
 * @param rateLimit         polite outbound throttle
 */
@ConfigurationProperties(prefix = "ebay")
public record EbayProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("") String clientId,
        @DefaultValue("") String clientSecret,
        @DefaultValue("https://api.ebay.com") String apiBaseUrl,
        @DefaultValue("EBAY_DE") Marketplace defaultMarketplace,
        @DefaultValue("617") String categoryId,
        @DefaultValue("3") int resultsPerQuery,
        @DefaultValue RateLimit rateLimit,
        @DefaultValue Quota quota
) {

    /** Whether eBay can actually be called: the flag is set <em>and</em> both credentials exist. */
    public boolean active() {
        return enabled && isSet(clientId) && isSet(clientSecret);
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public String toString() {
        return ("EbayProperties[enabled=%s, clientId=%s, clientSecret=%s, apiBaseUrl=%s, "
                + "defaultMarketplace=%s, categoryId=%s, resultsPerQuery=%d, rateLimit=%s, quota=%s]")
                .formatted(enabled, masked(clientId), masked(clientSecret), apiBaseUrl,
                        defaultMarketplace, categoryId, resultsPerQuery, rateLimit, quota);
    }

    private static String masked(String credential) {
        return isSet(credential) ? "<set>" : "<unset>";
    }

    /**
     * @param requestsPerSecond max requests/second sent to eBay, in aggregate across every user
     *                          ({@code <= 0} disables throttling)
     */
    public record RateLimit(@DefaultValue("2") double requestsPerSecond) {
    }

    /**
     * The daily call budget and when it rolls over (ADR-0017).
     *
     * @param dailyCallBudget    hard global ceiling per quota day, deliberately set to the full
     *                           allowance rather than a safety margin below it — the guard against
     *                           miscounting is eBay's own quota response, not a buffer
     * @param perUserOverbooking divisor in the per-user split {@code dailyCallBudget * f / n}.
     *                           At the requested {@code 5000 / (n/2)} this is 2: the budget is
     *                           knowingly handed out twice over, on the assumption that at most
     *                           half the registered users fetch prices on any given day
     * @param resetZone          zone eBay's daily allowance rolls over in. A zone, never a fixed
     *                           offset, because Pacific time observes daylight saving
     * @param resetTime          time of day the allowance rolls over. Configurable because the
     *                           "12 o'clock Pacific" hypothesis is both unverified and ambiguous
     */
    public record Quota(
            @DefaultValue("5000") int dailyCallBudget,
            @DefaultValue("2") int perUserOverbooking,
            @DefaultValue("America/Los_Angeles") ZoneId resetZone,
            @DefaultValue("00:00") LocalTime resetTime
    ) {
        public Quota {
            if (dailyCallBudget <= 0) {
                throw new IllegalArgumentException("ebay.quota.daily-call-budget must be positive");
            }
            if (perUserOverbooking <= 0) {
                throw new IllegalArgumentException("ebay.quota.per-user-overbooking must be positive");
            }
        }
    }
}
