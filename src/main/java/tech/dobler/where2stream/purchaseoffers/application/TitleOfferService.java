package tech.dobler.where2stream.purchaseoffers.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.dobler.where2stream.purchaseoffers.adapter.out.ebay.EbayProperties;
import tech.dobler.where2stream.purchaseoffers.domain.Marketplace;
import tech.dobler.where2stream.purchaseoffers.domain.OfferLookupResult;
import tech.dobler.where2stream.purchaseoffers.domain.OfferSourceUnavailableException;
import tech.dobler.where2stream.purchaseoffers.domain.QuotaVerdict;
import tech.dobler.where2stream.purchaseoffers.domain.TitleOffers;
import tech.dobler.where2stream.purchaseoffers.domain.UpstreamQuotaExhaustedException;
import tech.dobler.where2stream.purchaseoffers.port.out.PurchaseOfferSource;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.shared.platform.time.TimeService;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Looks up purchase offers for a title, and is the one place that decides not to.
 *
 * <p>Three guards sit in front of the actual call, each for a different reason:
 * <ul>
 *   <li>a <strong>circuit breaker</strong>, so a source that is already refusing is not hammered
 *       into refusing for longer — and so failing calls stop burning daily budget;</li>
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

    private final PurchaseOfferSource source;
    private final QuotaService quotaService;
    private final EbayProperties properties;
    private final TimeService timeService;

    private final ConcurrentMap<LookupKey, CompletableFuture<TitleOffers>> inFlight = new ConcurrentHashMap<>();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile Instant breakerOpenUntil = Instant.MIN;

    public TitleOfferService(PurchaseOfferSource source, QuotaService quotaService,
                             EbayProperties properties, TimeService timeService) {
        this.source = source;
        this.quotaService = quotaService;
        this.properties = properties;
        this.timeService = timeService;
    }

    /**
     * @param userId      whose allowance the call is booked against
     * @param imdbId      the title
     * @param searchTerm  built by the caller from data we already hold, never from the request
     * @param marketplace from the user's own setting
     */
    public OfferLookupResult lookup(UUID userId, ImdbId imdbId, String searchTerm, Marketplace marketplace) {
        if (breakerIsOpen()) {
            log.debug("eBay lookup for {} skipped: circuit breaker open until {}", imdbId, breakerOpenUntil);
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
            consecutiveFailures.set(0);
            ours.complete(offers);
            return OfferLookupResult.fetched(offers);
        } catch (UpstreamQuotaExhaustedException e) {
            ours.completeExceptionally(e);
            // eBay's word closes the day outright — it outranks our own counter (ADR-0017).
            quotaService.recordExhaustedByUpstream(e.getMessage());
            return OfferLookupResult.of(QuotaVerdict.GLOBAL_BUDGET_EXHAUSTED);
        } catch (OfferSourceUnavailableException e) {
            ours.completeExceptionally(e);
            recordFailure(imdbId, e);
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
            // The lookup we attached to failed. Its own caller already classified and counted that
            // failure, so this one only reports it — counting again would trip the breaker twice
            // for a single upstream call.
            return OfferLookupResult.unavailable();
        }
    }

    private boolean breakerIsOpen() {
        return timeService.now().isBefore(breakerOpenUntil);
    }

    private void recordFailure(ImdbId imdbId, RuntimeException cause) {
        final var failures = consecutiveFailures.incrementAndGet();
        final var threshold = properties.circuitBreaker().failureThreshold();
        if (failures >= threshold) {
            breakerOpenUntil = timeService.now().plus(properties.circuitBreaker().openFor());
            consecutiveFailures.set(0);
            log.warn("eBay lookups suspended until {} after {} consecutive failures (last for {}): {}",
                    breakerOpenUntil, failures, imdbId, cause.toString());
        } else {
            log.warn("eBay lookup for {} failed ({}/{} before suspending): {}",
                    imdbId, failures, threshold, cause.toString());
        }
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
