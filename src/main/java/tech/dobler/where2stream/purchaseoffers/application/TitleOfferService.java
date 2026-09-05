package tech.dobler.where2stream.purchaseoffers.application;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.dobler.where2stream.purchaseoffers.domain.Marketplace;
import tech.dobler.where2stream.purchaseoffers.domain.OfferLookupResult;
import tech.dobler.where2stream.purchaseoffers.domain.OfferSourceUnavailableException;
import tech.dobler.where2stream.purchaseoffers.domain.QuotaVerdict;
import tech.dobler.where2stream.purchaseoffers.domain.TitleOffers;
import tech.dobler.where2stream.purchaseoffers.domain.UpstreamQuotaExhaustedException;
import tech.dobler.where2stream.purchaseoffers.port.out.PurchaseOfferSource;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Looks up purchase offers for a title, and is the one place that decides not to.
 *
 * <p>Three guards sit in front of the actual call, each for a different reason:
 * <ul>
 *   <li>a <strong>circuit breaker</strong>, so a source that is already refusing is not hammered
 *       into refusing for longer — and so failing calls stop burning daily budget. It is
 *       resilience4j's, declared on {@code EbayBrowseApiSource.findOffers} and configured in
 *       {@code application.properties};</li>
 *   <li>the <strong>quota</strong> (ADR-0017), which rations the shared allowance between users;</li>
 *   <li><strong>in-flight deduplication</strong>, so two people asking for the same title at the
 *       same moment cost one upstream call rather than two. This is the piece the browser cache
 *       cannot provide: it covers a second user, a second tab, and a script with a valid session
 *       cookie (plan, section 5.6).</li>
 * </ul>
 *
 * <p>Nothing here throws for an expected failure. Every outcome — including "we could not ask" —
 * comes back as an {@link OfferLookupResult}, because a price widget failing to load must not turn
 * into an error page.
 *
 * <p>No caching of results: prices go stale in minutes, and a stored one would be wrong rather than
 * merely old (plan, section 5.6). Deduplication is not a cache — it only joins requests that
 * overlap in time.
 */
@Slf4j
@Service
public class TitleOfferService {

    /** Must match the instance name on {@code EbayBrowseApiSource.findOffers}. */
    static final String BREAKER_NAME = "ebay";

    private final PurchaseOfferSource source;
    private final QuotaService quotaService;
    private final CircuitBreakerRegistry circuitBreakers;

    private final ConcurrentMap<LookupKey, CompletableFuture<TitleOffers>> inFlight = new ConcurrentHashMap<>();

    public TitleOfferService(PurchaseOfferSource source, QuotaService quotaService,
                             CircuitBreakerRegistry circuitBreakers) {
        this.source = source;
        this.quotaService = quotaService;
        this.circuitBreakers = circuitBreakers;
    }

    /**
     * @param userId      whose allowance the call is booked against
     * @param imdbId      the title
     * @param searchTerm  built by the caller from data we already hold, never from the request
     * @param marketplace from the user's own setting
     */
    public OfferLookupResult lookup(UUID userId, ImdbId imdbId, String searchTerm, Marketplace marketplace) {
        if (breakerIsOpen()) {
            log.debug("eBay lookup for {} skipped: circuit breaker is open", imdbId);
            return OfferLookupResult.unavailable();
        }

        final var key = new LookupKey(imdbId, marketplace);
        final var ours = new CompletableFuture<TitleOffers>();
        final var running = inFlight.putIfAbsent(key, ours);
        if (running != null) {
            return joinRunningLookup(imdbId, running);
        }

        try {
            // Reserved only once the deduplication slot is ours, so the joiners above do not each
            // book calls for a request that is already paid for.
            final var verdict = quotaService.tryReserve(userId, CALLS_PER_LOOKUP);
            if (verdict != QuotaVerdict.ALLOWED) {
                ours.completeExceptionally(new OfferSourceUnavailableException("quota refused: " + verdict));
                return OfferLookupResult.of(verdict);
            }
            final var offers = source.findOffers(imdbId, searchTerm, marketplace);
            ours.complete(offers);
            return OfferLookupResult.fetched(offers);
        } catch (UpstreamQuotaExhaustedException e) {
            ours.completeExceptionally(e);
            // eBay's word closes the day outright — it outranks our own counter (ADR-0017).
            quotaService.recordExhaustedByUpstream(e.getMessage());
            return OfferLookupResult.of(QuotaVerdict.GLOBAL_BUDGET_EXHAUSTED);
        } catch (CallNotPermittedException e) {
            // The breaker opened between our pre-check and the call. Rare, and the only cost is the
            // reservation we already made — see the note on breakerIsOpen().
            ours.completeExceptionally(e);
            log.debug("eBay lookup for {} short-circuited by the open breaker", imdbId);
            return OfferLookupResult.unavailable();
        } catch (OfferSourceUnavailableException e) {
            ours.completeExceptionally(e);
            log.warn("eBay lookup for {} failed: {}", imdbId, e.toString());
            return OfferLookupResult.unavailable();
        } finally {
            inFlight.remove(key, ours);
        }
    }

    /** Attaches to a lookup someone else already started, instead of asking eBay a second time. */
    private OfferLookupResult joinRunningLookup(ImdbId imdbId, CompletableFuture<TitleOffers> running) {
        try {
            log.debug("Joining an in-flight eBay lookup for {}", imdbId);
            return OfferLookupResult.fetched(running.join());
        } catch (CompletionException | java.util.concurrent.CancellationException e) {
            // The lookup we attached to failed. Its own caller classified it, and the breaker
            // already recorded the single upstream call that actually happened — joiners must not
            // be counted again, which is exactly what attaching instead of re-calling achieves.
            return OfferLookupResult.unavailable();
        }
    }

    /**
     * Asks the breaker's state <em>before</em> reserving quota.
     *
     * <p>Without this pre-check the aspect would short-circuit the call after the reservation was
     * booked, charging the user and the shared budget for two calls that never left the building.
     * The check is advisory — the state can flip in the gap — which is why the
     * {@link CallNotPermittedException} branch above still exists. Losing a reservation in that
     * narrow window is acceptable; losing one on every request while the breaker is open is not.
     */
    private boolean breakerIsOpen() {
        return circuitBreakers.circuitBreaker(BREAKER_NAME).getState() == CircuitBreaker.State.OPEN;
    }

    /** Fixed-price and auction are separate calls, so one lookup costs two (plan, section 3). */
    private static final int CALLS_PER_LOOKUP = 2;

    /**
     * Deduplication is per title <em>and</em> marketplace: two users on different marketplaces are
     * asking different questions and must not share one answer.
     */
    private record LookupKey(ImdbId imdbId, Marketplace marketplace) {
    }
}
