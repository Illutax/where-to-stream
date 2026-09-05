package tech.dobler.where2stream.purchaseoffers.adapter.in.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.dobler.where2stream.accountaccess.port.in.CurrentUserPort;
import tech.dobler.where2stream.purchaseoffers.application.TitleOfferService;
import tech.dobler.where2stream.purchaseoffers.application.dto.TitleOffersDto;
import tech.dobler.where2stream.purchaseoffers.domain.OfferLookupResult;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;

import java.time.Duration;

/**
 * Current eBay prices for a title on the caller's own watchlist.
 *
 * <p>The endpoint takes an IMDb id, never a search string. The eBay search term is built on the
 * server from data we already hold, and the title has to be on the requesting user's watchlist —
 * without both, this would be an open, authenticated proxy to eBay's search (plan, section 5.1).
 *
 * <p>Every outcome is an HTTP 200 carrying a status field. The only 4xx here is the 404 for a
 * title that is not on the caller's watchlist; "no offers", "unavailable" and "budget spent" are
 * results, not errors (see {@link TitleOffersDto}).
 */
@RestController
@RequestMapping("/api/titles")
@RequiredArgsConstructor
public class PurchaseOfferApiController {

    /**
     * Long enough to absorb a re-render, a back-and-forth navigation or a double click; short
     * enough that the price does not visibly lag (plan, section 5.6).
     */
    private static final Duration BROWSER_CACHE = Duration.ofMinutes(5);

    private final TitleOfferService titleOfferService;
    private final CurrentUserPort currentUserPort;

    @GetMapping("/{imdbId}/offers")
    public ResponseEntity<TitleOffersDto> offers(Authentication authentication,
                                                 @PathVariable ImdbId imdbId) {
        final var userId = currentUserPort.resolveId(authentication.getName());
        final var result = titleOfferService.lookupForWatchlistTitle(userId, imdbId);
        return ResponseEntity.ok()
                .cacheControl(cacheControlFor(result))
                .body(TitleOffersDto.from(result));
    }

    /**
     * {@code private}, never {@code public}: the response depends on the caller's watchlist and is
     * charged against their personal allowance, so it must not sit in a shared cache.
     *
     * <p>Only a successful lookup is cacheable. A refusal is not — caching "your budget is spent"
     * for 5 minutes would keep telling the user that after the situation has changed, and caching
     * "unavailable" would outlast the circuit breaker's own recovery.
     *
     * <p>An explicit {@code Cache-Control} is required rather than merely useful: Spring Security
     * sets {@code no-store} on everything by default, and only a header set here overrides it (the
     * same reason {@code PosterApiController} sets one).
     *
     * <p>Refreshing on demand is the client's job — a changing query parameter bypasses the browser
     * cache. The server deliberately does not offer a "force" flag: that would be a way to spend
     * budget faster with no gate in front of it.
     */
    private static CacheControl cacheControlFor(OfferLookupResult result) {
        return result.status() == OfferLookupResult.Status.FETCHED
                ? CacheControl.maxAge(BROWSER_CACHE).cachePrivate()
                : CacheControl.noStore();
    }
}
