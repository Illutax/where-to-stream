package tech.dobler.werstreamt.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Service;
import tech.dobler.werstreamt.configurations.ImdbPosterProperties;
import tech.dobler.werstreamt.domain.ImdbId;
import tech.dobler.werstreamt.domain.PosterSize;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * IMDb-backed {@link PosterSource} (the default): resolves a title's poster URL via IMDb's public
 * GraphQL API ({@code title(id).primaryImage.url}, an {@code m.media-amazon.com} image), then
 * downloads the bytes pre-sized straight from Amazon's image CDN. The CDN resizes and re-compresses
 * on the fly via URL params (the {@code _V1_} transform), so a small, aggressively-compressed
 * thumbnail and a larger hover image are two requests against the same base URL — no server-side
 * image processing. All failures degrade to empty (logged). Outbound requests are throttled
 * (default 2 req/s) to stay polite.
 *
 * <p>Note: scraping the HTML title page does not work server-side — {@code www.imdb.com} returns an
 * empty {@code 202} to datacenter IPs (anti-bot) — hence the GraphQL API. The API data is IMDb's,
 * under their terms (limited non-commercial use).
 */
@Slf4j
@Service
public class ImdbPosterSource implements PosterSource {

    private static final String V1 = "_V1_";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    /** Minimal GraphQL query; the id is passed as a variable (never string-interpolated). */
    private static final String QUERY = "query($id:ID!){title(id:$id){primaryImage{url}}}";

    private final ImdbPosterProperties properties;
    private final HttpClient httpClient;
    private final long minIntervalNanos;
    private long nextAllowedNanos = System.nanoTime();

    public ImdbPosterSource(ImdbPosterProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .proxy(ProxySelector.getDefault())
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        final double rps = properties.rateLimit().requestsPerSecond();
        this.minIntervalNanos = rps <= 0 ? 0 : (long) (TimeUnit.SECONDS.toNanos(1) / rps);
    }

    @Override
    public Optional<String> findPosterPath(ImdbId imdbId) {
        final var uri = URI.create(properties.apiUrl());
        try {
            acquire();
            log.debug("Fetching poster metadata for {} from {}", imdbId, uri);
            final var request = HttpRequest.newBuilder(uri)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody(imdbId)))
                    .build();
            final var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            final var body = response.body();
            log.trace("Poster metadata for {}: HTTP {} ({} bytes)", imdbId, response.statusCode(), body.length());
            if (response.statusCode() != 200) {
                log.warn("IMDb poster lookup for {} returned HTTP {}", imdbId, response.statusCode());
                return Optional.empty();
            }
            final var url = parsePosterUrl(body);
            url.ifPresentOrElse(
                    resolved -> log.debug("Resolved poster for {}: {}", imdbId, resolved),
                    () -> log.debug("No poster for {}", imdbId));
            return url;
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("IMDb poster lookup for {} failed: {}", imdbId, e.toString());
            return Optional.empty();
        }
    }

    @Override
    public Optional<byte[]> download(String posterPath, PosterSize size) {
        if (posterPath == null || posterPath.isBlank()) {
            return Optional.empty();
        }
        final var uri = URI.create(sizedUrl(posterPath, properties.widthFor(size), properties.qualityFor(size)));
        try {
            acquire();
            log.debug("Downloading {} poster image: GET {}", size, uri);
            final var response = httpClient.send(HttpRequest.newBuilder(uri).GET()
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body().length == 0) {
                log.warn("IMDb {} image download {} returned HTTP {} ({} bytes)",
                        size, uri, response.statusCode(), response.body().length);
                return Optional.empty();
            }
            log.trace("Downloaded {} poster ({} bytes) from {}", size, response.body().length, uri);
            return Optional.of(response.body());
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("IMDb {} image download failed for {}: {}", size, uri, e.toString());
            return Optional.empty();
        }
    }

    private static String requestBody(ImdbId imdbId) {
        // imdbId is tt\w+-validated, so it needs no JSON escaping; still passed as a GraphQL variable.
        return "{\"query\":\"" + QUERY + "\",\"variables\":{\"id\":\"" + imdbId.value() + "\"}}";
    }

    /**
     * The poster URL from an IMDb GraphQL {@code title.primaryImage.url} response, or empty when the
     * title has no poster / the response carries {@code errors}. Network-free (unit-testable). Uses
     * Spring Boot's {@code JsonParser} so it is agnostic to the JSON library on the classpath.
     */
    static Optional<String> parsePosterUrl(String json) {
        try {
            final Map<String, Object> root = JsonParserFactory.getJsonParser().parseMap(json);
            if (root.get("data") instanceof Map<?, ?> data
                    && data.get("title") instanceof Map<?, ?> title
                    && title.get("primaryImage") instanceof Map<?, ?> image
                    && image.get("url") instanceof String url && !url.isBlank()) {
                return Optional.of(url);
            }
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * Rebuilds an Amazon media URL with resizer params (target width + JPEG quality), replacing any
     * transform already on it. The CDN serves the image pre-sized, so no server-side image
     * processing is needed. A URL without the {@code _V1_} marker is returned unchanged.
     */
    static String sizedUrl(String base, int width, int quality) {
        final int idx = base.indexOf(V1);
        if (idx < 0) {
            return base;
        }
        return base.substring(0, idx + V1.length()) + "QL" + quality + "_UX" + width + "_.jpg";
    }

    private synchronized void acquire() {
        if (minIntervalNanos == 0) {
            return;
        }
        final long now = System.nanoTime();
        if (now < nextAllowedNanos) {
            final long waitNanos = nextAllowedNanos - now;
            log.trace("Throttled outbound poster request by {}ms", TimeUnit.NANOSECONDS.toMillis(waitNanos));
            try {
                TimeUnit.NANOSECONDS.sleep(waitNanos);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            nextAllowedNanos += minIntervalNanos;
        } else {
            nextAllowedNanos = now + minIntervalNanos;
        }
    }
}
