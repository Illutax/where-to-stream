package tech.dobler.where2stream.titlecatalog.adapter.out.imdb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Service;
import tech.dobler.where2stream.titlecatalog.adapter.out.HttpClientFactory;
import tech.dobler.where2stream.titlecatalog.adapter.out.OutboundHttpClients;
import tech.dobler.where2stream.titlecatalog.adapter.out.imdb.ImdbSearchProperties;
import tech.dobler.where2stream.shared.domain.ImdbId;
import tech.dobler.where2stream.shared.domain.ReleaseYear;
import tech.dobler.where2stream.shared.outbound.RateLimiter;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Title search against IMDb's public suggestion/typeahead endpoint —
 * the same JSON API IMDb's own site search box uses, verified reachable and stable,
 * and simpler than IMDb's GraphQL {@code mainSearch}
 * (unused elsewhere in this codebase, schema unverified).
 * Free-text discovery across IMDb's whole catalog has no local equivalent,
 * unlike per-id metadata lookups (see {@link ImdbTitleClient}),
 * which stay served from our own DB cache first and are never duplicated here.
 * The response mixes title ({@code tt…}), person ({@code nm…}) and company ({@code co…}) hits;
 * only titles are kept. Failures degrade to an empty list (logged).
 * Outbound requests are throttled (own {@link RateLimiter}, configured from {@code imdb-search.rate-limit}).
 */
@Slf4j
@Service
public class ImdbSuggestionClient {

    /** A single title hit; {@code year} is {@code 0} ("not yet released"/unknown) when absent. */
    public record ImdbSuggestion(ImdbId imdbId, String name, ReleaseYear year) {
    }

    private final ImdbSearchProperties properties;
    private final HttpClient httpClient;
    private final RateLimiter rateLimiter;

    public ImdbSuggestionClient(ImdbSearchProperties properties, HttpClientFactory httpClientFactory) {
        this.properties = properties;
        this.httpClient = httpClientFactory.newClient();
        this.rateLimiter = new RateLimiter(properties.rateLimit().requestsPerSecond());
    }

    /** Searches IMDb by title text; empty list on a blank query, any failure, or no matches. */
    public List<ImdbSuggestion> search(String query) {
        final var trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        try {
            final var uri = buildUri(properties.apiUrl(), trimmed);
            rateLimiter.acquire();
            log.debug("Searching IMDb suggestions for '{}' via {}", trimmed, uri);
            final var request = HttpRequest.newBuilder(uri)
                    .header("User-Agent", OutboundHttpClients.USER_AGENT)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            final var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("IMDb suggestion lookup for '{}' returned HTTP {}", trimmed, response.statusCode());
                return List.of();
            }
            return parse(response.body());
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("IMDb suggestion lookup for '{}' failed: {}", trimmed, e.toString());
            return List.of();
        }
    }

    /**
     * Builds the suggestion-endpoint URI for a (non-blank) query.
     * Both the leading-character path segment and the query itself are percent-encoded,
     * so a query starting with a character that would otherwise break the URI
     * (a bare {@code %}, a quote, a backslash, a space, …)
     * degrades to a harmless encoded segment instead of making {@link URI#create} throw.
     * Network-free (unit-testable).
     */
    static URI buildUri(String apiUrl, String query) {
        final var firstChar = URLEncoder.encode(String.valueOf(Character.toLowerCase(query.charAt(0))), StandardCharsets.UTF_8);
        return URI.create(apiUrl + "/" + firstChar + "/" + URLEncoder.encode(query, StandardCharsets.UTF_8) + ".json");
    }

    /**
     * Parses the {@code {"d":[...]}} suggestion payload, keeping only real title hits
     * (a failed {@link ImdbId} construction marks a person/company id, silently skipped).
     * Network-free (unit-testable).
     */
    static List<ImdbSuggestion> parse(String json, int maxResults) {
        try {
            final Map<String, Object> root = JsonParserFactory.getJsonParser().parseMap(json);
            if (!(root.get("d") instanceof List<?> hits)) {
                return List.of();
            }
            final var results = new ArrayList<ImdbSuggestion>();
            for (Object hit : hits) {
                if (results.size() >= maxResults) {
                    break;
                }
                toSuggestion(hit).ifPresent(results::add);
            }
            return results;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private List<ImdbSuggestion> parse(String json) {
        return parse(json, properties.maxResults());
    }

    private static Optional<ImdbSuggestion> toSuggestion(Object hit) {
        if (!(hit instanceof Map<?, ?> entry)
                || !(entry.get("id") instanceof String id)
                || !(entry.get("l") instanceof String name) || name.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ImdbSuggestion(ImdbId.of(id), name, year(entry)));
        } catch (IllegalArgumentException e) {
            return Optional.empty(); // not a title id (person/company)
        }
    }

    private static ReleaseYear year(Map<?, ?> entry) {
        return entry.get("y") instanceof Number y ? ReleaseYear.of(y.intValue()) : ReleaseYear.of(0);
    }
}
