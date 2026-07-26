package tech.dobler.werstreamt.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import tech.dobler.werstreamt.configurations.TmdbProperties;
import tech.dobler.werstreamt.domain.ImdbId;
import tech.dobler.werstreamt.domain.PosterSize;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * TMDB-backed {@link PosterSource}: resolves an IMDb id to a {@code poster_path} via the
 * {@code /find} endpoint, then downloads the pre-sized image bytes from the TMDB image CDN. One
 * {@code find} call per title; images come from the CDN. All failures degrade to empty (logged).
 */
@Slf4j
@Service
public class TmdbPosterSource implements PosterSource {

    private final TmdbProperties properties;
    private final HttpClient httpClient;
    private final long minIntervalNanos;
    private long nextAllowedNanos = System.nanoTime();

    public TmdbPosterSource(TmdbProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .proxy(ProxySelector.getDefault())
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        final double rps = properties.rateLimit().requestsPerSecond();
        this.minIntervalNanos = rps <= 0 ? 0 : (long) (TimeUnit.SECONDS.toNanos(1) / rps);
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
            log.debug("TMDB find for {}: GET {}", imdbId, redactApiKey(uri));
            final var body = getString(uri);
            return body.flatMap(TmdbPosterSource::parsePosterPath);
        } catch (RuntimeException e) {
            log.warn("TMDB find failed for {}: {}", imdbId, e.toString());
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
            log.debug("TMDB {} image download: GET {}", size, uri);
            acquire();
            final var response = httpClient.send(HttpRequest.newBuilder(uri).GET()
                    .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body().length == 0) {
                log.warn("TMDB image {} returned {}", uri, response.statusCode());
                return Optional.empty();
            }
            return Optional.of(response.body());
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("TMDB image download failed for {}: {}", uri, e.toString());
            return Optional.empty();
        }
    }

    private Optional<String> getString(URI uri) {
        try {
            acquire();
            final var response = httpClient.send(HttpRequest.newBuilder(uri).GET()
                    .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 ? Optional.of(response.body()) : Optional.empty();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    /**
     * The first movie (or TV) {@code poster_path} in a TMDB {@code /find} response, or empty.
     * Network-free (unit-testable). Uses Spring Boot's {@code JsonParser} so it is agnostic to the
     * JSON library on the classpath.
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

    private synchronized void acquire() {
        if (minIntervalNanos == 0) {
            return;
        }
        final long now = System.nanoTime();
        if (now < nextAllowedNanos) {
            try {
                TimeUnit.NANOSECONDS.sleep(nextAllowedNanos - now);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            nextAllowedNanos += minIntervalNanos;
        } else {
            nextAllowedNanos = now + minIntervalNanos;
        }
    }
}
