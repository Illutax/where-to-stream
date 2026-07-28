package tech.dobler.werstreamt.configurations;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Binding for the {@code imdb-search.*} configuration — the navbar title search, backed by IMDb's
 * public (no-auth) suggestion/typeahead endpoint (the same JSON API IMDb's own site search box
 * uses). Outbound requests are throttled (default 5 req/s) — a single global limit shared across
 * every user's search (the client-side debounce keeps a single well-behaved browser tab well
 * under this; the limit's job is bounding the *aggregate* rate across all concurrent users, not
 * re-implementing the debounce server-side).
 *
 * @param apiUrl     the IMDb suggestion endpoint base ({@code {apiUrl}/{firstChar}/{query}.json})
 * @param rateLimit  outbound throttle for the suggestion lookups
 * @param maxResults max number of title hits returned per search
 */
@ConfigurationProperties(prefix = "imdb-search")
public record ImdbSearchProperties(
        @DefaultValue("https://v2.sg.media-imdb.com/suggestion") String apiUrl,
        @DefaultValue RateLimit rateLimit,
        @DefaultValue("8") int maxResults
) {
    /**
     * @param requestsPerSecond max requests/second sent to IMDb's suggestion endpoint, in
     *                          aggregate across every user (≤ 0 disables throttling)
     */
    public record RateLimit(@DefaultValue("5") double requestsPerSecond) {
    }
}
