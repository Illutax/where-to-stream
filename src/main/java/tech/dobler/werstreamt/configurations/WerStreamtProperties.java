package tech.dobler.werstreamt.configurations;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Central binding for the {@code wer-streamt.*} configuration.
 *
 * @param path       directory that holds the IMDb export CSV files (asset lists)
 * @param invalidate cache-invalidation settings
 */
@ConfigurationProperties(prefix = "wer-streamt")
public record WerStreamtProperties(
        String path,
        @DefaultValue Invalidate invalidate
) {
    /**
     * @param afterDays number of days after which a cached query result is considered stale
     */
    public record Invalidate(@DefaultValue("28") int afterDays) {
    }
}
