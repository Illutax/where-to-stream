package tech.dobler.where2stream.configurations;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Source-agnostic {@code poster.*} configuration shared by every poster source.
 *
 * @param negativeCacheDays how long a "no poster" result is honoured before the source is asked again
 */
@ConfigurationProperties(prefix = "poster")
public record PosterProperties(
        @DefaultValue("14") int negativeCacheDays
) {
}
