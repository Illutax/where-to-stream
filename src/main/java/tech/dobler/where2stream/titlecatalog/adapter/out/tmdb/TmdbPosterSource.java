package tech.dobler.where2stream.titlecatalog.adapter.out.tmdb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import tech.dobler.where2stream.titlecatalog.adapter.out.HttpClientFactory;
import tech.dobler.where2stream.titlecatalog.adapter.out.OutboundHttpClients;
import tech.dobler.where2stream.titlecatalog.adapter.out.tmdb.TmdbProperties;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.shared.platform.outbound.RateLimiter;
import tech.dobler.where2stream.titlecatalog.domain.PosterSize;
import tech.dobler.where2stream.titlecatalog.port.out.PosterPort;

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
 * TMDB-backed {@link PosterPort}: resolves an IMDb id to a {@code poster_path} via the {@code /find} endpoint,
 * then downloads the pre-sized image bytes from the TMDB image CDN.
 * One {@code find} call per title; images come from the CDN. All failures degrade to empty (logged).
 * Outbound requests are throttled (own {@link RateLimiter}, configured from {@code tmdb.rate-limit}).
 */
@Slf4j
@Service
public class TmdbPosterSource implements PosterPort {

    private final TmdbProperties properties;
    private final HttpClient httpClient;
    private final RateLimiter rateLimiter;

    public TmdbPosterSource(TmdbProperties properties, HttpClientFactory httpClientFactory) {
        this.properties = properties;
        this.httpClient = httpClientFactory.newClient();
        this.rateLimiter = new RateLimiter(properties.rateLimit().requestsPerSecond());
    }

    @Override
    public Optional<String> findPosterPath(ImdbId imdbId) {
        if (!properties.active()) {
            return Optional.empty();
        }
        final var uri = UriComponentsBuilder.fromUriString(properties.apiBaseUrl())
                .pathSegment("find", imdbId.value())
                .queryParam("external_source", "imdb_id")
                .queryParam("api_key", properties.apiKey())
                .build().toUri();
        try {
            rateLimiter.acquire();
            log.debug("Fetching poster metadata for {} from {}", imdbId, redactApiKey(uri));
            final var response = httpClient.send(HttpRequest.newBuilder(uri).GET()
                    .header("User-Agent", OutboundHttpClients.USER_AGENT)
                    .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString());
            log.trace("Poster metadata for {}: HTTP {} ({} bytes)", imdbId, response.statusCode(), response.body().length());
            if (response.statusCode() != 200) {
                log.warn("TMDB find for {} returned HTTP {}", imdbId, response.statusCode());
                return Optional.empty();
            }
            final var path = parsePosterPath(response.body());
            path.ifPresentOrElse(
                    resolved -> log.debug("Resolved poster for {}: {}", imdbId, resolved),
                    () -> log.debug("No poster for {}", imdbId));
            return path;
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("TMDB find for {} failed: {}", imdbId, e.toString());
            return Optional.empty();
        }
    }

    @Override
    public Optional<byte[]> download(String posterPath, PosterSize size) {
        if (posterPath == null || posterPath.isBlank()) {
            return Optional.empty();
        }
        final var uri = URI.create(properties.imageBaseUrl() + "/" + tmdbSize(size) + posterPath);
        try {
            rateLimiter.acquire();
            log.debug("Downloading {} poster image: GET {}", size, uri);
            final var response = httpClient.send(HttpRequest.newBuilder(uri).GET()
                    .header("User-Agent", OutboundHttpClients.USER_AGENT)
                    .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body().length == 0) {
                log.warn("TMDB {} image download {} returned HTTP {} ({} bytes)",
                        size, uri, response.statusCode(), response.body().length);
                return Optional.empty();
            }
            log.trace("Downloaded {} poster ({} bytes) from {}", size, response.body().length, uri);
            return Optional.of(response.body());
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("TMDB {} image download failed for {}: {}", size, uri, e.toString());
            return Optional.empty();
        }
    }

    /**
     * The first movie (or TV) {@code poster_path} in a TMDB {@code /find} response, or empty.
     * Network-free (unit-testable).
     * Uses Spring Boot's {@code JsonParser} so it is agnostic to the JSON library on the classpath.
     */
    static Optional<String> parsePosterPath(String json) {
        try {
            final Map<String, Object> root = JsonParserFactory.getJsonParser().parseMap(json);
            for (String key : List.of("movie_results", "tv_results")) {
                if (root.get(key) instanceof List<?> results && !results.isEmpty()
                        && results.get(0) instanceof Map<?, ?> first
                        && first.get("poster_path") instanceof String path && !path.isBlank()) {
                    return Optional.of(path);
                }
            }
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /** Keeps the api_key out of the logs. */
    private static String redactApiKey(URI uri) {
        return uri.toString().replaceAll("(api_key=)[^&]*", "$1***");
    }

    private static String tmdbSize(PosterSize size) {
        return size == PosterSize.THUMB ? "w92" : "w500";
    }

}
