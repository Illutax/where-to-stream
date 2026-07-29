package tech.dobler.where2stream.titlecatalog.adapter.out.imdb;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import tech.dobler.where2stream.titlecatalog.domain.PosterSize;

/**
 * Binding for the {@code imdb-poster.*} configuration — the <strong>default</strong> poster source,
 * which looks a title's poster URL up via IMDb's GraphQL API and serves it from Amazon's image CDN.
 * The CDN resizes on the fly via URL params, so both sizes are requested pre-sized (no server-side
 * image processing): the thumbnail is small and aggressively compressed, the hover image larger.
 * Outbound requests are throttled (default 10 req/s) to stay polite — the GraphQL API and the
 * Amazon image CDN both tolerate this comfortably.
 *
 * @param apiUrl       the IMDb GraphQL endpoint ({@code title(id).primaryImage.url} lookup)
 * @param rateLimit    outbound throttle for the API lookups and image downloads
 * @param thumbWidth   target width (px) of the row thumbnail
 * @param thumbQuality JPEG quality (1–100) of the row thumbnail — low, to keep it tiny
 * @param fullWidth    target width (px) of the hover image
 * @param fullQuality  JPEG quality (1–100) of the hover image
 */
@ConfigurationProperties(prefix = "imdb-poster")
public record ImdbPosterProperties(
        @DefaultValue("https://api.graphql.imdb.com/") String apiUrl,
        @DefaultValue RateLimit rateLimit,
        @DefaultValue("100") int thumbWidth,
        @DefaultValue("50") int thumbQuality,
        @DefaultValue("600") int fullWidth,
        @DefaultValue("85") int fullQuality
) {
    /** Target width for a size variant. */
    public int widthFor(PosterSize size) {
        return size == PosterSize.THUMB ? thumbWidth : fullWidth;
    }

    /** JPEG quality for a size variant. */
    public int qualityFor(PosterSize size) {
        return size == PosterSize.THUMB ? thumbQuality : fullQuality;
    }

    /**
     * @param requestsPerSecond max requests/second sent to IMDb / the image CDN
     *                          (≤ 0 disables throttling)
     */
    public record RateLimit(@DefaultValue("10") double requestsPerSecond) {
    }
}
