package tech.dobler.where2stream.titlecatalog.adapter.out.tmdb;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import tech.dobler.where2stream.titlecatalog.port.in.PosterAttributionPort;

/**
 * Binding for the {@code tmdb.*} configuration (poster images via The Movie Database API). TMDB is
 * an <strong>opt-in alternative</strong> poster source: by default posters are scraped from IMDb
 * (see {@link ImdbPosterProperties}). Set {@link #enabled()} <em>and</em> an {@link #apiKey()} to
 * source posters from TMDB instead (which also shows the required TMDB attribution footer).
 *
 * @param enabled       feature flag: use TMDB as the poster source (requires an API key)
 * @param apiKey        TMDB v3 API key; without it TMDB stays inactive even when {@code enabled}
 * @param apiBaseUrl    TMDB REST base (the {@code /find} endpoint)
 * @param imageBaseUrl  TMDB image CDN base (pre-sized JPEGs, e.g. {@code /w92/…})
 * @param rateLimit     polite outbound throttle for TMDB requests
 */
@ConfigurationProperties(prefix = "tmdb")
public record TmdbProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("") String apiKey,
        @DefaultValue("https://api.themoviedb.org/3") String apiBaseUrl,
        @DefaultValue("https://image.tmdb.org/t/p") String imageBaseUrl,
        @DefaultValue RateLimit rateLimit
) implements PosterAttributionPort {
    /** Whether TMDB is the active poster source: the flag is set <em>and</em> a key is configured. */
    public boolean active() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    @Override
    public boolean tmdbAttributionRequired() {
        return active();
    }

    /**
     * @param requestsPerSecond max requests/second sent to TMDB ({@code <= 0} disables throttling)
     */
    public record RateLimit(@DefaultValue("10") double requestsPerSecond) {
    }
}
