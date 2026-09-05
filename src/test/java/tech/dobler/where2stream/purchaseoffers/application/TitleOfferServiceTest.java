package tech.dobler.where2stream.purchaseoffers.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.where2stream.purchaseoffers.EbayPropertiesFixture;
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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TitleOfferServiceTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final ImdbId HEAT = ImdbId.of("tt0113277");
    private static final Instant NOW = Instant.parse("2026-09-05T14:00:00Z");

    @Mock
    private PurchaseOfferSource source;
    @Mock
    private QuotaService quotaService;
    @Mock
    private TimeService timeService;

    private TitleOfferService service;

    private static EbayProperties properties() {
        return EbayPropertiesFixture.active();
    }

    @BeforeEach
    void setUp() {
        service = new TitleOfferService(source, quotaService, properties(), timeService);
        lenient().when(timeService.now()).thenReturn(NOW);
    }

    private OfferLookupResult lookup() {
        return service.lookup(USER, HEAT, "Heat Blu-ray", Marketplace.EBAY_DE);
    }

    private static TitleOffers noOffers() {
        return TitleOffers.none(HEAT, NOW);
    }

    // --- happy path and quota ----------------------------------------------------------------

    @Test
    void anAllowedLookupBooksTwoCallsAndReturnsTheOffers() {
        when(quotaService.tryReserve(USER, 2)).thenReturn(QuotaVerdict.ALLOWED);
        when(source.findOffers(HEAT, "Heat Blu-ray", Marketplace.EBAY_DE)).thenReturn(noOffers());

        assertThat(lookup())
                .isNotNull()
                .extracting(OfferLookupResult::status, r -> r.offers().isPresent())
                .isEqualTo(java.util.List.of(OfferLookupResult.Status.FETCHED, true));
    }

    @Test
    void findingNothingIsStillASuccessfulLookup() {
        when(quotaService.tryReserve(USER, 2)).thenReturn(QuotaVerdict.ALLOWED);
        when(source.findOffers(any(), any(), any())).thenReturn(noOffers());

        // "No offers" must not read as a failure — nobody selling a film is an ordinary answer.
        assertThat(lookup().status()).isEqualTo(OfferLookupResult.Status.FETCHED);
    }

    @Test
    void aUserOutOfAllowanceIsToldSoAndNoCallIsMade() {
        when(quotaService.tryReserve(USER, 2)).thenReturn(QuotaVerdict.USER_ALLOWANCE_REACHED);

        assertThat(lookup().status()).isEqualTo(OfferLookupResult.Status.USER_ALLOWANCE_REACHED);
        verify(source, never()).findOffers(any(), any(), any());
    }

    @Test
    void anExhaustedSharedBudgetIsReportedSeparatelyFromTheUsersOwn() {
        when(quotaService.tryReserve(USER, 2)).thenReturn(QuotaVerdict.GLOBAL_BUDGET_EXHAUSTED);

        assertThat(lookup().status()).isEqualTo(OfferLookupResult.Status.GLOBAL_BUDGET_EXHAUSTED);
        verify(source, never()).findOffers(any(), any(), any());
    }

    // --- failures ----------------------------------------------------------------------------

    @Test
    void anUnreachableSourceDegradesInsteadOfThrowing() {
        when(quotaService.tryReserve(USER, 2)).thenReturn(QuotaVerdict.ALLOWED);
        when(source.findOffers(any(), any(), any()))
                .thenThrow(new OfferSourceUnavailableException("upstream refused"));

        // A price widget that cannot load must not become an error page.
        assertThat(lookup().status()).isEqualTo(OfferLookupResult.Status.UNAVAILABLE);
    }

    @Test
    void ebayReportingItsQuotaSpentClosesTheDayRatherThanTrippingTheBreaker() {
        when(quotaService.tryReserve(USER, 2)).thenReturn(QuotaVerdict.ALLOWED);
        when(source.findOffers(any(), any(), any()))
                .thenThrow(new UpstreamQuotaExhaustedException("errorId 2001"));

        assertThat(lookup().status()).isEqualTo(OfferLookupResult.Status.GLOBAL_BUDGET_EXHAUSTED);
        verify(quotaService).recordExhaustedByUpstream("errorId 2001");
    }

    // --- circuit breaker ---------------------------------------------------------------------

    @Test
    void repeatedFailuresSuspendFurtherCalls() {
        when(quotaService.tryReserve(eq(USER), anyInt())).thenReturn(QuotaVerdict.ALLOWED);
        when(source.findOffers(any(), any(), any()))
                .thenThrow(new OfferSourceUnavailableException("upstream refused"));

        for (int attempt = 0; attempt < 3; attempt++) {
            assertThat(lookup().status()).isEqualTo(OfferLookupResult.Status.UNAVAILABLE);
        }
        // Fourth attempt is refused before reaching eBay: hammering a refusing upstream prolongs
        // the refusal, and each attempt would still cost budget.
        assertThat(lookup().status()).isEqualTo(OfferLookupResult.Status.UNAVAILABLE);
        verify(source, times(3)).findOffers(any(), any(), any());
    }

    @Test
    void callsResumeOnceTheBreakerWindowHasPassed() {
        when(quotaService.tryReserve(eq(USER), anyInt())).thenReturn(QuotaVerdict.ALLOWED);
        when(source.findOffers(any(), any(), any()))
                .thenThrow(new OfferSourceUnavailableException("upstream refused"));
        for (int attempt = 0; attempt < 3; attempt++) {
            lookup();
        }

        when(timeService.now()).thenReturn(NOW.plus(Duration.ofMinutes(6)));
        lookup();

        verify(source, times(4)).findOffers(any(), any(), any());
    }

    @Test
    void aSuccessfulLookupClearsTheFailureCount() {
        when(quotaService.tryReserve(eq(USER), anyInt())).thenReturn(QuotaVerdict.ALLOWED);
        when(source.findOffers(any(), any(), any()))
                .thenThrow(new OfferSourceUnavailableException("first"))
                .thenThrow(new OfferSourceUnavailableException("second"))
                .thenReturn(noOffers())
                .thenThrow(new OfferSourceUnavailableException("third"))
                .thenThrow(new OfferSourceUnavailableException("fourth"));

        for (int attempt = 0; attempt < 5; attempt++) {
            lookup();
        }

        // Four failures in total, but never three in a row — the breaker must not have opened.
        verify(source, times(5)).findOffers(any(), any(), any());
    }

    // --- in-flight deduplication --------------------------------------------------------------

    @Test
    void twoConcurrentLookupsForTheSameTitleCostOneUpstreamCall() throws Exception {
        final var released = new CountDownLatch(1);
        final var bothInside = new CountDownLatch(1);
        final var upstreamCalls = new AtomicInteger();
        when(quotaService.tryReserve(eq(USER), anyInt())).thenReturn(QuotaVerdict.ALLOWED);
        when(source.findOffers(any(), any(), any())).thenAnswer(invocation -> {
            upstreamCalls.incrementAndGet();
            bothInside.countDown();
            released.await(5, TimeUnit.SECONDS);
            return noOffers();
        });

        try (var pool = Executors.newFixedThreadPool(2)) {
            final var first = pool.submit(this::lookup);
            bothInside.await(5, TimeUnit.SECONDS);
            final var second = pool.submit(this::lookup);
            Thread.sleep(100); // let the second reach the deduplication slot
            released.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS).status()).isEqualTo(OfferLookupResult.Status.FETCHED);
            assertThat(second.get(5, TimeUnit.SECONDS).status()).isEqualTo(OfferLookupResult.Status.FETCHED);
        }

        // The browser cache cannot cover this case: two users, two tabs, or a script with a
        // session cookie (plan, section 5.6).
        assertThat(upstreamCalls).hasValue(1);
        verify(quotaService, times(1)).tryReserve(eq(USER), anyInt());
    }

    @Test
    void lookupsForDifferentMarketplacesAreNotDeduplicatedTogether() {
        when(quotaService.tryReserve(eq(USER), anyInt())).thenReturn(QuotaVerdict.ALLOWED);
        when(source.findOffers(any(), any(), any())).thenReturn(noOffers());

        service.lookup(USER, HEAT, "Heat Blu-ray", Marketplace.EBAY_DE);
        service.lookup(USER, HEAT, "Heat Blu-ray", Marketplace.EBAY_US);

        // Different marketplaces are different questions and must not share one answer.
        verify(source).findOffers(HEAT, "Heat Blu-ray", Marketplace.EBAY_DE);
        verify(source).findOffers(HEAT, "Heat Blu-ray", Marketplace.EBAY_US);
    }

    @Test
    void aSecondLookupAfterTheFirstFinishedStartsAfresh() {
        when(quotaService.tryReserve(eq(USER), anyInt())).thenReturn(QuotaVerdict.ALLOWED);
        when(source.findOffers(any(), any(), any())).thenReturn(noOffers());

        lookup();
        lookup();

        // Deduplication is not a cache: it only joins requests that genuinely overlap in time.
        verify(source, times(2)).findOffers(any(), any(), any());
    }
}
