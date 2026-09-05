package tech.dobler.where2stream.purchaseoffers.adapter.out.ebay;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import tech.dobler.where2stream.purchaseoffers.domain.Marketplace;
import tech.dobler.where2stream.purchaseoffers.domain.Offer;
import tech.dobler.where2stream.purchaseoffers.domain.OfferPrice;
import tech.dobler.where2stream.purchaseoffers.domain.OfferSourceUnavailableException;
import tech.dobler.where2stream.purchaseoffers.domain.TitleOffers;
import tech.dobler.where2stream.purchaseoffers.domain.UpstreamQuotaExhaustedException;
import tech.dobler.where2stream.purchaseoffers.port.out.PurchaseOfferSource;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.shared.platform.outbound.HttpClientFactory;
import tech.dobler.where2stream.shared.platform.outbound.OutboundHttpClients;
import tech.dobler.where2stream.shared.platform.outbound.RateLimiter;
import tech.dobler.where2stream.shared.platform.time.TimeService;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads purchase offers from the eBay Browse API ({@code buy/browse/v1/item_summary/search}).
 *
 * <p>Modelled on {@code ImdbSuggestionSource}, the project's existing JSON/REST integration: an
 * {@link HttpClientFactory} seam so tests inject a mocked client, a {@link RateLimiter} of its own,
 * and static package-private parsing so the response mapping is unit-testable without a network.
 *
 * <p><strong>The response mapping is written against eBay's documentation and has never seen a real
 * response</strong> — at the time of writing the developer account was still pending. That is why
 * every field is read defensively and why the mapping lives in {@link #cheapestOffer}: when the
 * real payload turns out to differ, the correction is confined to one method and its tests.
 *
 * <p>Two calls are made per lookup, one filtered to fixed-price listings and one to auctions,
 * because the two prices come from different fields ({@code price} vs. {@code currentBidPrice}).
 * Whether a single combined call could serve both is unverified (plan, section 8); if it can, the
 * daily budget in titles doubles.
 */
@Slf4j
@Service
public class EbayBrowseApiSource implements PurchaseOfferSource {

    private static final String SEARCH_PATH = "/buy/browse/v1/item_summary/search";

    private final EbayProperties properties;
    private final EbayOAuthTokenProvider tokenProvider;
    private final HttpClient httpClient;
    private final RateLimiter rateLimiter;
    private final TimeService timeService;

    public EbayBrowseApiSource(EbayProperties properties, EbayOAuthTokenProvider tokenProvider,
                               HttpClientFactory httpClientFactory, TimeService timeService) {
        this.properties = properties;
        this.tokenProvider = tokenProvider;
        this.httpClient = httpClientFactory.newClient();
        this.rateLimiter = new RateLimiter(properties.rateLimit().requestsPerSecond());
        this.timeService = timeService;
    }

    @Override
    public TitleOffers findOffers(ImdbId imdbId, String searchTerm, Marketplace marketplace) {
        if (!properties.active()) {
            throw new OfferSourceUnavailableException(
                    "eBay integration is not configured (ebay.enabled plus both credentials)");
        }
        final var buyNow = lookup(searchTerm, marketplace, BuyingOption.FIXED_PRICE);
        final var auction = lookup(searchTerm, marketplace, BuyingOption.AUCTION);
        final var offers = new TitleOffers(imdbId, buyNow, auction, timeService.now());
        if (!offers.hasAnyOffer()) {
            // Section 5.5: without this, a mapping broken by an API change is indistinguishable
            // from a title nobody sells — both simply show "no offers".
            log.info("eBay lookup for {} ('{}', {}) produced no price", imdbId, searchTerm, marketplace);
        }
        return offers;
    }

    private Optional<Offer> lookup(String searchTerm, Marketplace marketplace, BuyingOption buyingOption) {
        final var uri = searchUri(properties.apiBaseUrl(), searchTerm, properties.categoryId(),
                properties.resultsPerQuery(), buyingOption);
        final var request = HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer " + tokenProvider.accessToken())
                .header("X-EBAY-C-MARKETPLACE-ID", marketplace.marketplaceId())
                .header("Accept", "application/json")
                .header("User-Agent", OutboundHttpClients.USER_AGENT)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        try {
            rateLimiter.acquire();
            final var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                final var detail = "eBay %s search returned HTTP %d: %s"
                        .formatted(buyingOption, response.statusCode(), response.body());
                if (looksLikeQuotaExhaustion(response.statusCode(), response.body())) {
                    logQuotaRejection(response);
                    throw new UpstreamQuotaExhaustedException(detail);
                }
                logRejection(response);
                throw new OfferSourceUnavailableException(detail);
            }
            return cheapestOffer(response.body(), marketplace, buyingOption);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OfferSourceUnavailableException("eBay %s search was interrupted".formatted(buyingOption), e);
        } catch (IOException e) {
            throw new OfferSourceUnavailableException("eBay %s search failed".formatted(buyingOption), e);
        }
    }

    /**
     * Everything eBay might tell us about the quota, dumped verbatim.
     *
     * <p>This exists to settle two guesses that the code currently rests on and that no primary
     * source confirms (ADR-0017, plan section 8): <em>which</em> response means "allowance spent",
     * and <em>when</em> the allowance rolls over. Both are answerable from production logs and from
     * nowhere else, so the moment they can be observed must not be wasted on a one-line warning.
     *
     * <p>Logged at {@code warn} rather than {@code debug} deliberately: this happens at most a
     * handful of times a day, and if it is filtered out by a log level, the evidence is gone.
     */
    private void logQuotaRejection(HttpResponse<String> response) {
        log.warn("""
                        eBay looks like it refused on quota grounds. Recording everything, because \
                        both the detection and the reset time are unverified guesses (ADR-0017).
                          status : {}
                          when   : {} (UTC)
                          headers: {}
                          body   : {}""",
                response.statusCode(), timeService.now(), quotaRelevantHeaders(response), response.body());
    }

    /** An ordinary rejection — logged more briefly, but with the headers that might reclassify it. */
    private void logRejection(HttpResponse<String> response) {
        log.warn("eBay search rejected with HTTP {} (headers: {}). Not recognised as a quota "
                        + "rejection — if the daily budget was in fact spent, looksLikeQuotaExhaustion "
                        + "needs correcting.",
                response.statusCode(), quotaRelevantHeaders(response));
    }

    /**
     * Picks out the headers that could carry quota information.
     *
     * <p>Which names eBay actually uses is part of what is being established here, so the filter is
     * broad on purpose: anything mentioning a limit, a quota, a rate or a retry. Header names are
     * matched case-insensitively because HTTP does not guarantee their casing.
     */
    private static String quotaRelevantHeaders(HttpResponse<String> response) {
        final var interesting = response.headers().map().entrySet().stream()
                .filter(entry -> {
                    final var name = entry.getKey().toLowerCase(java.util.Locale.ROOT);
                    return name.contains("limit") || name.contains("quota") || name.contains("rate")
                            || name.contains("retry") || name.contains("reset")
                            || name.startsWith("x-ebay");
                })
                .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
                .toList();
        return interesting.isEmpty() ? "<none matched>" : String.join("; ", interesting);
    }

    /**
     * Whether a rejection is eBay saying the daily allowance is spent, rather than an ordinary
     * failure — the two are handled very differently (ADR-0017).
     *
     * <p><strong>Unverified.</strong> HTTP 429 plus error id 2001 is what the documentation
     * suggests, but no real response has been seen. Getting this wrong is survivable: an
     * unrecognised quota rejection is simply treated as an ordinary failure, so the circuit breaker
     * handles it instead of the quota ledger. Confirming it is an open point in the plan.
     */
    static boolean looksLikeQuotaExhaustion(int statusCode, String body) {
        return statusCode == 429
                || (body != null && body.contains("\"errorId\"") && body.contains("2001"));
    }

    /**
     * Builds the search URI. Static and network-free so the query parameters are testable on their
     * own; {@code UriComponentsBuilder} handles the encoding of the search term.
     */
    static URI searchUri(String apiBaseUrl, String searchTerm, String categoryId, int limit,
                         BuyingOption buyingOption) {
        return UriComponentsBuilder.fromUriString(apiBaseUrl + SEARCH_PATH)
                .queryParam("q", searchTerm)
                .queryParam("category_ids", categoryId)
                .queryParam("filter", "buyingOptions:{%s}".formatted(buyingOption.name()))
                .queryParam("sort", "price")
                .queryParam("limit", limit)
                .build()
                .encode()
                .toUri();
    }

    /**
     * Picks the cheapest usable offer from a search response.
     *
     * <p>Deliberately forgiving about individual entries and strict about the ones it keeps: an
     * item summary missing a price, carrying a currency we do not support, or linking somewhere
     * other than the queried marketplace is skipped rather than failing the whole lookup — one odd
     * listing should not cost the user their price. The result is re-sorted by
     * {@link Offer#total()} rather than trusting eBay's {@code sort=price}, because the sort
     * eBay applies does not account for the shipping cost the plan's decision 6.3 includes.
     */
    static Optional<Offer> cheapestOffer(String json, Marketplace marketplace, BuyingOption buyingOption) {
        final Map<String, Object> root;
        try {
            root = JsonParserFactory.getJsonParser().parseMap(json);
        } catch (RuntimeException e) {
            throw new OfferSourceUnavailableException("eBay search response was not readable JSON", e);
        }
        if (!(root.get("itemSummaries") instanceof List<?> summaries)) {
            return Optional.empty();
        }
        return summaries.stream()
                .filter(Map.class::isInstance)
                .map(summary -> toOffer((Map<?, ?>) summary, marketplace, buyingOption))
                .flatMap(Optional::stream)
                .min(Comparator.comparing(Offer::total));
    }

    private static Optional<Offer> toOffer(Map<?, ?> summary, Marketplace marketplace, BuyingOption buyingOption) {
        final var price = amount(summary.get(buyingOption.priceField()), marketplace);
        if (price.isEmpty()) {
            return Optional.empty();
        }
        final var url = itemUrl(summary.get("itemWebUrl"), marketplace);
        if (url.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Offer(price.get(), shippingCost(summary, marketplace), url.get()));
    }

    /** Reads {@code {"value": "12.34", "currency": "EUR"}}, rejecting anything unexpected. */
    private static Optional<OfferPrice> amount(Object node, Marketplace marketplace) {
        if (!(node instanceof Map<?, ?> money)
                || !(money.get("value") instanceof String value)
                || !(money.get("currency") instanceof String currency)) {
            return Optional.empty();
        }
        if (!currency.equals(marketplace.currency().getCurrencyCode())) {
            log.warn("Skipping an offer priced in {} on marketplace {}", currency, marketplace);
            return Optional.empty();
        }
        try {
            return Optional.of(OfferPrice.ofMajorUnits(new BigDecimal(value), currency));
        } catch (IllegalArgumentException e) { // covers NumberFormatException from BigDecimal too
            log.warn("Skipping an offer with an unreadable amount '{}' {}: {}", value, currency, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Reads the first shipping option's cost.
     * An absent or unreadable value means "not stated" — never "free", see {@link Offer}.
     */
    private static Optional<OfferPrice> shippingCost(Map<?, ?> summary, Marketplace marketplace) {
        if (!(summary.get("shippingOptions") instanceof List<?> options) || options.isEmpty()) {
            return Optional.empty();
        }
        if (!(options.getFirst() instanceof Map<?, ?> first)) {
            return Optional.empty();
        }
        return amount(first.get("shippingCost"), marketplace);
    }

    /** Applies the host allowlist from section 5.3 before an eBay-supplied URL reaches the client. */
    private static Optional<URI> itemUrl(Object node, Marketplace marketplace) {
        if (!(node instanceof String raw) || raw.isBlank()) {
            return Optional.empty();
        }
        final URI url;
        try {
            url = URI.create(raw);
        } catch (IllegalArgumentException e) {
            log.warn("Skipping an offer with an unparseable itemWebUrl");
            return Optional.empty();
        }
        if (!marketplace.allowsHostOf(url)) {
            log.warn("Skipping an offer whose itemWebUrl points outside {}", marketplace.baseDomain());
            return Optional.empty();
        }
        return Optional.of(url);
    }

    /** The two buying options the feature asks about, and where each keeps its price. */
    enum BuyingOption {
        FIXED_PRICE("price"),
        AUCTION("currentBidPrice");

        private final String priceField;

        BuyingOption(String priceField) {
            this.priceField = priceField;
        }

        String priceField() {
            return priceField;
        }
    }
}
