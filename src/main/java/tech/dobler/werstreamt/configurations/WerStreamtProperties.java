package tech.dobler.werstreamt.configurations;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Central binding for the {@code wer-streamt.*} configuration.
 *
 * @param path       directory that holds the IMDb export CSV files (asset lists)
 * @param invalidate cache-invalidation settings
 * @param rateLimit  outbound throttling for werstreamt.es requests
 */
@ConfigurationProperties(prefix = "wer-streamt")
public record WerStreamtProperties(
        String path,
        @DefaultValue Invalidate invalidate,
        @DefaultValue RateLimit rateLimit
) {
    /**
     * @param afterDays number of days after which a cached query result is considered stale
     */
    public record Invalidate(@DefaultValue("28") int afterDays) {
    }

    /**
     * @param requestsPerSecond max requests/second sent to werstreamt.es
     *                          (≤ 0 disables throttling)
     */
    public record RateLimit(@DefaultValue("2") double requestsPerSecond) {
    }
}
