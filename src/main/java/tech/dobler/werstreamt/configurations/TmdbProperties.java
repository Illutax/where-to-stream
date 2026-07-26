package tech.dobler.werstreamt.configurations;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Binding for the {@code tmdb.*} configuration (poster images via The Movie Database API). The
 * feature is <strong>optional</strong>: with a blank {@link #apiKey()} it is disabled and posters
 * are simply unavailable (the app runs unchanged), just like the optional Google OIDC login.
 *
 * @param apiKey           TMDB v3 API key; blank disables the feature
 * @param apiBaseUrl       TMDB REST base (the {@code /find} endpoint)
 * @param imageBaseUrl     TMDB image CDN base (pre-sized JPEGs, e.g. {@code /w92/…})
 * @param rateLimit        polite outbound throttle for TMDB requests
 * @param negativeCacheDays how long a "no poster" result is cached before TMDB is asked again
 */
@ConfigurationProperties(prefix = "tmdb")
public record TmdbProperties(
        @DefaultValue("") String apiKey,
        @DefaultValue("https://api.themoviedb.org/3") String apiBaseUrl,
        @DefaultValue("https://image.tmdb.org/t/p") String imageBaseUrl,
        @DefaultValue RateLimit rateLimit,
        @DefaultValue("14") int negativeCacheDays
) {
    /** Whether the poster feature is configured (an API key is present). */
    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * @param requestsPerSecond max requests/second sent to TMDB ({@code <= 0} disables throttling)
     */
    public record RateLimit(@DefaultValue("10") double requestsPerSecond) {
    }
}
