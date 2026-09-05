package tech.dobler.where2stream.purchaseoffers;

import tech.dobler.where2stream.purchaseoffers.adapter.out.ebay.EbayProperties;
import tech.dobler.where2stream.purchaseoffers.domain.Marketplace;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Shared {@link EbayProperties} fixture for the purchase-offers tests.
 *
 * <p>Exists because the record grew three times while this feature was being built, and each time
 * every test class that constructed one had to be edited. One place to change beats four.
 * Throttling is off by default so tests do not sleep.
 */
public final class EbayPropertiesFixture {

    public static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");

    private EbayPropertiesFixture() {
    }

    /** Fully configured and enabled, with the production defaults for quota and breaker. */
    public static EbayProperties active() {
        return with(true, "client-id", "client-secret", quota(5000, 2));
    }

    public static EbayProperties enabled(boolean enabled) {
        return with(enabled, "client-id", "client-secret", quota(5000, 2));
    }

    public static EbayProperties withCredentials(String clientId, String clientSecret) {
        return with(true, clientId, clientSecret, quota(5000, 2));
    }

    public static EbayProperties withQuota(EbayProperties.Quota quota) {
        return with(true, "client-id", "client-secret", quota);
    }

    public static EbayProperties.Quota quota(int dailyCallBudget, int perUserOverbooking) {
        return new EbayProperties.Quota(dailyCallBudget, perUserOverbooking, PACIFIC, LocalTime.MIDNIGHT);
    }

    private static EbayProperties with(boolean enabled, String clientId, String clientSecret,
                                       EbayProperties.Quota quota) {
        return new EbayProperties(enabled, clientId, clientSecret, "https://api.ebay.com",
                Marketplace.EBAY_DE, "617", 3, new EbayProperties.RateLimit(0), quota,
                new EbayProperties.CircuitBreaker(3, Duration.ofMinutes(5)));
    }
}
