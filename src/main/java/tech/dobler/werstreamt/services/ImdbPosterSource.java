package tech.dobler.werstreamt.services;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
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
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * IMDb-backed {@link PosterSource} (the default): scrapes a title's poster URL from its IMDb page
 * ({@code og:image}, an Amazon image-CDN URL) and downloads the bytes pre-sized straight from the
 * CDN. Amazon's image server resizes and re-compresses on the fly via URL params (the {@code _V1_}
 * transform), so a small, aggressively-compressed thumbnail and a larger hover image are two
 * different requests against the same base URL — no server-side image processing. All failures
 * degrade to empty (logged). Outbound requests are throttled (default 2 req/s) to avoid a block.
 */
@Slf4j
@Service
public class ImdbPosterSource implements PosterSource {

    private static final String V1 = "_V1_";

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
        final var query = UriComponentsBuilder.fromUriString(properties.titleBaseUrl())
                .pathSegment(imdbId.value(), "") // trailing slash: .../title/tt.../
                .build();
        try {
            acquire();
            return parsePosterUrl(ApiClientUtils.getConnectionWithUserAgent(query).get());
        } catch (HttpStatusException e) {
            log.warn("IMDb poster lookup for {} failed: {}", imdbId, e.getMessage());
        } catch (IOException | RuntimeException e) {
            log.warn("IMDb poster lookup for {} failed: {}", imdbId, e.toString());
        }
        return Optional.empty();
    }

    @Override
    public Optional<byte[]> download(String posterPath, PosterSize size) {
        if (posterPath == null || posterPath.isBlank()) {
            return Optional.empty();
        }
        final var uri = URI.create(sizedUrl(posterPath, properties.widthFor(size), properties.qualityFor(size)));
        try {
            acquire();
            final var response = httpClient.send(HttpRequest.newBuilder(uri).GET()
                    .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body().length == 0) {
                log.warn("IMDb image {} returned {}", uri, response.statusCode());
                return Optional.empty();
            }
            return Optional.of(response.body());
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("IMDb image download failed for {}: {}", uri, e.toString());
            return Optional.empty();
        }
    }

    /**
     * The poster image URL from a title page's {@code og:image} — an {@code m.media-amazon.com}
     * poster — or empty when the page has none (a title without a poster). Network-free, so the
     * parsing is unit-testable with a fixture.
     */
    static Optional<String> parsePosterUrl(Document document) {
        final var og = document.selectFirst("meta[property=og:image]");
        if (og == null) {
            return Optional.empty();
        }
        final var url = og.attr("content");
        return isPosterImage(url) ? Optional.of(url) : Optional.empty();
    }

    private static boolean isPosterImage(String url) {
        return url != null && url.contains("media-amazon.com/images/M/");
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
