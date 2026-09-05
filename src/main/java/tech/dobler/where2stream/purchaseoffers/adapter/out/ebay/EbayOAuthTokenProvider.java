package tech.dobler.where2stream.purchaseoffers.adapter.out.ebay;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Component;
import tech.dobler.where2stream.purchaseoffers.domain.OfferSourceUnavailableException;
import tech.dobler.where2stream.shared.platform.outbound.HttpClientFactory;
import tech.dobler.where2stream.shared.platform.outbound.OutboundHttpClients;
import tech.dobler.where2stream.shared.platform.time.TimeService;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * Obtains and caches the eBay <em>application</em> access token (OAuth2 client-credentials grant).
 *
 * <p>There is no user involved: the token authenticates <em>this application</em>, which is why it
 * can be shared across all callers and cached in memory. It is never persisted, never logged and
 * never sent to the browser — a token in the browser would be extractable, which is one of the
 * reasons the plan rules out calling eBay from the client at all (section 3).
 *
 * <p>Renewal happens {@link #RENEW_BEFORE_EXPIRY} early rather than exactly at expiry, so a token
 * cannot go stale in flight between our check and eBay receiving the request.
 *
 * <p>Time is read through {@link TimeService} (ADR-0003), which also makes the expiry logic
 * testable against a fixed clock instead of a sleep.
 */
@Slf4j
@Component
public class EbayOAuthTokenProvider {

    /** Renew this far ahead of the stated expiry, so a token cannot expire mid-request. */
    static final Duration RENEW_BEFORE_EXPIRY = Duration.ofMinutes(5);

    private static final String SCOPE = "https://api.ebay.com/oauth/api_scope";

    private final EbayProperties properties;
    private final HttpClient httpClient;
    private final TimeService timeService;

    private String cachedToken;
    private Instant renewAt = Instant.MIN;

    public EbayOAuthTokenProvider(EbayProperties properties, HttpClientFactory httpClientFactory,
                                  TimeService timeService) {
        this.properties = properties;
        this.httpClient = httpClientFactory.newClient();
        this.timeService = timeService;
    }

    /**
     * Returns a valid application token, fetching a new one only when the cached one is missing or
     * close to expiry.
     *
     * <p>{@code synchronized} because concurrent lookups would otherwise each request their own
     * token: harmless for correctness, but every one of those requests counts against the daily
     * call budget the whole feature is rationing (ADR-0017).
     *
     * @throws OfferSourceUnavailableException if eBay refuses the credentials or cannot be reached
     */
    public synchronized String accessToken() {
        if (cachedToken != null && timeService.now().isBefore(renewAt)) {
            return cachedToken;
        }
        final var token = requestToken();
        cachedToken = token.value();
        renewAt = timeService.now().plus(token.expiresIn()).minus(RENEW_BEFORE_EXPIRY);
        log.debug("Obtained a new eBay application token, renewing at {}", renewAt);
        return cachedToken;
    }

    private Token requestToken() {
        final var request = HttpRequest.newBuilder(URI.create(properties.apiBaseUrl() + "/identity/v1/oauth2/token"))
                .header("Authorization", "Basic " + basicCredentials())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", OutboundHttpClients.USER_AGENT)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(
                        "grant_type=client_credentials&scope=" + URLEncoder.encode(SCOPE, StandardCharsets.UTF_8)))
                .build();
        try {
            final var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                // The body may name the reason (invalid_client, invalid_scope, …) and contains no
                // secret of ours — the credentials travel in the request, not the response.
                throw new OfferSourceUnavailableException(
                        "eBay token request returned HTTP %d: %s".formatted(response.statusCode(), response.body()));
            }
            return parseToken(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OfferSourceUnavailableException("eBay token request was interrupted", e);
        } catch (IOException e) {
            throw new OfferSourceUnavailableException("eBay token request failed", e);
        }
    }

    private String basicCredentials() {
        final var raw = properties.clientId() + ":" + properties.clientSecret();
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Parses {@code {"access_token": "...", "expires_in": 7200, "token_type": "Application"}}.
     * Static and network-free so it is unit-testable on its own.
     */
    static Token parseToken(String json) {
        final Map<String, Object> root;
        try {
            root = JsonParserFactory.getJsonParser().parseMap(json);
        } catch (RuntimeException e) {
            throw new OfferSourceUnavailableException("eBay token response was not readable JSON", e);
        }
        if (!(root.get("access_token") instanceof String value) || value.isBlank()) {
            throw new OfferSourceUnavailableException("eBay token response carried no access_token");
        }
        if (!(root.get("expires_in") instanceof Number seconds)) {
            throw new OfferSourceUnavailableException("eBay token response carried no expires_in");
        }
        return new Token(value, Duration.ofSeconds(seconds.longValue()));
    }

    /** An application token and how long eBay says it stays valid. */
    record Token(String value, Duration expiresIn) {
    }
}
