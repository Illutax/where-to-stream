package tech.dobler.where2stream.streamingavailability.adapter.out.werstreamtes;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Central binding for the {@code wer-streamt.*} configuration.
 *
 * @param invalidate       cache-invalidation settings
 * @param rateLimit        outbound throttling for werstreamt.es requests
 * @param backgroundRefresh the scheduled proactive-refresh job (ADR-0016)
 */
@ConfigurationProperties(prefix = "wer-streamt")
public record WerStreamtProperties(
        @DefaultValue Invalidate invalidate,
        @DefaultValue RateLimit rateLimit,
        @DefaultValue BackgroundRefresh backgroundRefresh
) {
    /**
     * @param afterDays      number of days after which a cached query result is considered stale
     * @param jitterMinFactor lower bound (as a multiple of {@code afterDays}) of the randomised
     *                        due-for-background-refresh window written alongside each fresh scrape
     * @param jitterMaxFactor upper bound of that window; staggering refreshes between the two
     *                        factors avoids many titles cached together becoming due at once
     *                        (thundering herd)
     */
    public record Invalidate(
            @DefaultValue("28") int afterDays,
            @DefaultValue("1.5") double jitterMinFactor,
            @DefaultValue("2.0") double jitterMaxFactor
    ) {
    }

    /**
     * @param requestsPerSecond max requests/second sent to werstreamt.es
     *                          (≤ 0 disables throttling)
     */
    public record RateLimit(@DefaultValue("2") double requestsPerSecond) {
    }

    /**
     * @param enabled not-off switch for the scheduled job (e.g. disable on a demo/test instance)
     * @param cron    when the job runs; deliberately a coarse, once-daily default — it is a
     *                safety net for titles nobody is actively viewing, not a substitute for the
     *                demand-driven refresh path
     */
    public record BackgroundRefresh(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("0 0 4 * * *") String cron
    ) {
    }
}
