package tech.dobler.werstreamt.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tech.dobler.werstreamt.configurations.ImdbPosterProperties;
import tech.dobler.werstreamt.domain.ImdbId;
import tech.dobler.werstreamt.domain.PosterSize;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * IMDb-backed {@link PosterSource} (the default): the poster reference comes from the shared
 * {@link TitleMetaService} (one IMDb GraphQL fetch per title, reused by the age rating and the
 * future localized title), and the image bytes are downloaded pre-sized straight from Amazon's image
 * CDN. The CDN resizes and re-compresses on the fly via URL params (the {@code _V1_} transform), so
 * a small thumbnail and a larger hover image are two requests against the same base URL — no
 * server-side image processing. All failures degrade to empty (logged). Downloads are throttled
 * (own {@link RateLimiter}, default 10 req/s) to stay polite.
 */
@Slf4j
@Service
public class ImdbPosterSource implements PosterSource {

    private static final String V1 = "_V1_";

    private final ImdbPosterProperties properties;
    private final TitleMetaService titleMetaService;
    private final HttpClient httpClient;
    private final RateLimiter rateLimiter;

    @Autowired
    public ImdbPosterSource(ImdbPosterProperties properties, TitleMetaService titleMetaService) {
        this(properties, titleMetaService, OutboundHttpClients.newClient());
    }

    // Package-private: lets tests inject a mocked HttpClient instead of a real one.
    ImdbPosterSource(ImdbPosterProperties properties, TitleMetaService titleMetaService, HttpClient httpClient) {
        this.properties = properties;
        this.titleMetaService = titleMetaService;
        this.httpClient = httpClient;
        this.rateLimiter = new RateLimiter(properties.rateLimit().requestsPerSecond());
    }

    @Override
    public Optional<String> findPosterPath(ImdbId imdbId) {
        return titleMetaService.posterPath(imdbId);
    }

    @Override
    public Optional<byte[]> download(String posterPath, PosterSize size) {
        if (posterPath == null || posterPath.isBlank()) {
            return Optional.empty();
        }
        final var uri = URI.create(sizedUrl(posterPath, properties.widthFor(size), properties.qualityFor(size)));
        try {
            rateLimiter.acquire();
            log.debug("Downloading {} poster image: GET {}", size, uri);
            final var response = httpClient.send(HttpRequest.newBuilder(uri).GET()
                    .header("User-Agent", OutboundHttpClients.USER_AGENT)
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
}
