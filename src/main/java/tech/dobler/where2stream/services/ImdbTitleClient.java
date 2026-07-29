package tech.dobler.where2stream.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Service;
import tech.dobler.where2stream.configurations.ImdbPosterProperties;
import tech.dobler.where2stream.domain.AgeRating;
import tech.dobler.where2stream.shared.domain.ImdbId;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The single IMDb GraphQL data access for a title: <strong>one</strong> request returns everything
 * we cache per title — the poster URL and the age rating (and, later, the localized title) — so we
 * never hit the API more than once per film. All failures degrade to empty (logged); a parsed
 * response with no poster/rating is a valid result with null fields. Outbound requests are throttled
 * (own {@link RateLimiter}, configured from {@code imdb-poster.rate-limit}). The HTML title page is
 * unusable server-side ({@code www.imdb.com} returns an empty {@code 202} to datacenter IPs), hence
 * the GraphQL API; the data is IMDb's under their terms (limited non-commercial use).
 */
@Slf4j
@Service
public class ImdbTitleClient {

    /** The German FSK certificate lives under this country id; everything else is a fallback. */
    private static final String GERMANY = "DE";
    /** Minimal GraphQL query; the id is a variable (never string-interpolated). */
    private static final String QUERY =
            "query($id:ID!){title(id:$id){primaryImage{url} certificate{rating} "
                    + "certificates(first:60){edges{node{rating country{id}}}} "
                    + "akas(first:60){edges{node{text country{id}}}}}}";

    /** Everything we fetch and cache for a title in one request. Fields are null when unavailable. */
    public record ImdbTitleData(String posterUrl, AgeRating rating, String germanTitle) {
    }

    private final ImdbPosterProperties properties;
    private final HttpClient httpClient;
    private final RateLimiter rateLimiter;

    public ImdbTitleClient(ImdbPosterProperties properties, HttpClientFactory httpClientFactory) {
        this.properties = properties;
        this.httpClient = httpClientFactory.newClient();
        this.rateLimiter = new RateLimiter(properties.rateLimit().requestsPerSecond());
    }

    /** Fetches a title's metadata; empty only on a hard failure (so the caller can retry). */
    public Optional<ImdbTitleData> fetch(ImdbId imdbId) {
        final var uri = URI.create(properties.apiUrl());
        try {
            rateLimiter.acquire();
            log.debug("Fetching title metadata for {} from {}", imdbId, uri);
            final var request = HttpRequest.newBuilder(uri)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", OutboundHttpClients.USER_AGENT)
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody(imdbId)))
                    .build();
            final var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            final var body = response.body();
            log.trace("Title metadata for {}: HTTP {} ({} bytes)", imdbId, response.statusCode(), body.length());
            if (response.statusCode() != 200) {
                log.warn("IMDb title lookup for {} returned HTTP {}", imdbId, response.statusCode());
                return Optional.empty();
            }
            final var data = parse(body);
            log.debug("Resolved metadata for {}: poster={} rating={} germanTitle={}",
                    imdbId, data.posterUrl() != null, data.rating(), data.germanTitle());
            return Optional.of(data);
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("IMDb title lookup for {} failed: {}", imdbId, e.toString());
            return Optional.empty();
        }
    }

    private static String requestBody(ImdbId imdbId) {
        // imdbId is tt\w+-validated, so it needs no JSON escaping; still passed as a GraphQL variable.
        return "{\"query\":\"" + QUERY + "\",\"variables\":{\"id\":\"" + imdbId.value() + "\"}}";
    }

    /**
     * Parses the GraphQL response into {@link ImdbTitleData} (poster URL + age rating), tolerating
     * missing fields / {@code errors} (→ null fields). Network-free (unit-testable). Uses Spring
     * Boot's {@code JsonParser} so it is agnostic to the JSON library on the classpath.
     */
    static ImdbTitleData parse(String json) {
        try {
            final Map<String, Object> root = JsonParserFactory.getJsonParser().parseMap(json);
            if (root.get("data") instanceof Map<?, ?> data && data.get("title") instanceof Map<?, ?> title) {
                return new ImdbTitleData(parsePosterUrl(title), parseRating(title), parseGermanTitle(title));
            }
        } catch (RuntimeException e) {
            return new ImdbTitleData(null, null, null);
        }
        return new ImdbTitleData(null, null, null);
    }

    /** The German ({@code DE}) alternative title, or null if the title has none. */
    private static String parseGermanTitle(Map<?, ?> title) {
        if (title.get("akas") instanceof Map<?, ?> akas && akas.get("edges") instanceof List<?> edges) {
            for (Object edge : edges) {
                if (edge instanceof Map<?, ?> e && e.get("node") instanceof Map<?, ?> node
                        && node.get("country") instanceof Map<?, ?> country
                        && GERMANY.equals(country.get("id"))
                        && node.get("text") instanceof String text && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private static String parsePosterUrl(Map<?, ?> title) {
        if (title.get("primaryImage") instanceof Map<?, ?> image
                && image.get("url") instanceof String url && !url.isBlank()) {
            return url;
        }
        return null;
    }

    private static AgeRating parseRating(Map<?, ?> title) {
        // Prefer the German FSK certificate...
        if (title.get("certificates") instanceof Map<?, ?> certificates
                && certificates.get("edges") instanceof List<?> edges) {
            for (Object edge : edges) {
                if (edge instanceof Map<?, ?> e && e.get("node") instanceof Map<?, ?> node
                        && node.get("country") instanceof Map<?, ?> country
                        && GERMANY.equals(country.get("id"))
                        && node.get("rating") instanceof String rating && !rating.isBlank()) {
                    return AgeRating.fsk(rating);
                }
            }
        }
        // ...otherwise fall back to the primary (usually US) certificate.
        if (title.get("certificate") instanceof Map<?, ?> certificate
                && certificate.get("rating") instanceof String rating && !rating.isBlank()) {
            return AgeRating.other(rating);
        }
        return null;
    }
}
