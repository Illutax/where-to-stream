package tech.dobler.where2stream.purchaseoffers.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.where2stream.accountaccess.port.in.UserDirectoryPort;
import tech.dobler.where2stream.purchaseoffers.adapter.out.ebay.EbayProperties;
import tech.dobler.where2stream.purchaseoffers.domain.GlobalQuotaUsage;
import tech.dobler.where2stream.purchaseoffers.domain.Marketplace;
import tech.dobler.where2stream.purchaseoffers.domain.QuotaDay;
import tech.dobler.where2stream.purchaseoffers.domain.QuotaVerdict;
import tech.dobler.where2stream.purchaseoffers.domain.UserQuotaUsage;
import tech.dobler.where2stream.purchaseoffers.port.out.GlobalQuotaUsageRepository;
import tech.dobler.where2stream.purchaseoffers.port.out.UserQuotaUsageRepository;
import tech.dobler.where2stream.shared.platform.time.TimeService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotaServiceTest {

    /** 2026-01-15 12:00 UTC is 04:00 Pacific standard time, so the quota day is 2026-01-15. */
    private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");
    private static final LocalDate QUOTA_DATE = LocalDate.of(2026, 1, 15);
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Mock
    private GlobalQuotaUsageRepository globalUsage;
    @Mock
    private UserQuotaUsageRepository userUsage;
    @Mock
    private UserDirectoryPort userDirectory;
    @Mock
    private TimeService timeService;

    private QuotaService service;

    private static EbayProperties properties(int dailyBudget, int overbooking) {
        return new EbayProperties(true, "id", "secret", "https://api.ebay.com", Marketplace.EBAY_DE,
                "617", 3, new EbayProperties.RateLimit(0),
                new EbayProperties.Quota(dailyBudget, overbooking,
                        ZoneId.of("America/Los_Angeles"), LocalTime.MIDNIGHT));
    }

    @BeforeEach
    void setUp() {
        service = new QuotaService(properties(5000, 2), globalUsage, userUsage, userDirectory, timeService);
        lenient().when(timeService.now()).thenReturn(NOW);
    }

    // --- the per-user split ------------------------------------------------------------------

    @Test
    void thePerUserAllowanceIsTheBudgetHandedOutTwiceOver() {
        when(userDirectory.registeredUserCount()).thenReturn(5L);

        // 5000 * 2 / 5 — the shape the owner asked for, written as 5000 / (n/2).
        assertThat(service.perUserAllowance()).isEqualTo(2000);
    }

    @Test
    void aSingleUserGetsTwiceTheBudgetWhichOnlyTheGlobalCeilingThenLimits() {
        when(userDirectory.registeredUserCount()).thenReturn(1L);

        assertThat(service.perUserAllowance()).isEqualTo(10_000);
    }

    @Test
    void anEmptyUserTableDoesNotDivideByZero() {
        when(userDirectory.registeredUserCount()).thenReturn(0L);

        assertThat(service.perUserAllowance()).isEqualTo(10_000);
    }

    @Test
    void manyUsersStillLeaveEveryoneAtLeastOneCall() {
        when(userDirectory.registeredUserCount()).thenReturn(100_000L);

        assertThat(service.perUserAllowance()).isEqualTo(1);
    }

    // --- reserving ---------------------------------------------------------------------------

    @Test
    void aFirstLookupOfTheDayIsAllowedAndBooksBothCounters() {
        when(userDirectory.registeredUserCount()).thenReturn(5L);
        when(globalUsage.findById(QUOTA_DATE)).thenReturn(Optional.empty());
        when(userUsage.findByQuotaDayAndUserId(QUOTA_DATE, USER)).thenReturn(Optional.empty());

        assertThat(service.tryReserve(USER, 2)).isEqualTo(QuotaVerdict.ALLOWED);
        verify(globalUsage).save(any());
        verify(userUsage).save(any());
    }

    @Test
    void aUserPastTheirOwnAllowanceIsRefusedWithoutTouchingTheGlobalCounter() {
        when(userDirectory.registeredUserCount()).thenReturn(5L);
        when(globalUsage.findById(QUOTA_DATE)).thenReturn(Optional.of(usedGlobal(10)));
        when(userUsage.findByQuotaDayAndUserId(QUOTA_DATE, USER)).thenReturn(Optional.of(usedByUser(2000)));

        assertThat(service.tryReserve(USER, 2)).isEqualTo(QuotaVerdict.USER_ALLOWANCE_REACHED);
        verify(globalUsage, never()).save(any());
        verify(userUsage, never()).save(any());
    }

    @Test
    void theGlobalCeilingRefusesEvenAUserWithAllowanceLeft() {
        // The overbooking made this possible: this user has spent nothing, the shared budget is gone.
        when(globalUsage.findById(QUOTA_DATE)).thenReturn(Optional.of(usedGlobal(5000)));

        assertThat(service.tryReserve(USER, 2)).isEqualTo(QuotaVerdict.GLOBAL_BUDGET_EXHAUSTED);
        verify(userUsage, never()).save(any());
    }

    @Test
    void aReservationThatWouldCrossTheCeilingIsRefusedRatherThanTruncated() {
        when(globalUsage.findById(QUOTA_DATE)).thenReturn(Optional.of(usedGlobal(4999)));

        assertThat(service.tryReserve(USER, 2)).isEqualTo(QuotaVerdict.GLOBAL_BUDGET_EXHAUSTED);
    }

    @Test
    void aDayEbayDeclaredExhaustedIsClosedRegardlessOfOurOwnCount() {
        final var barelyUsed = usedGlobal(3);
        barelyUsed.markExhausted(NOW);
        when(globalUsage.findById(QUOTA_DATE)).thenReturn(Optional.of(barelyUsed));

        // Our arithmetic says there are thousands of calls left; eBay says otherwise, and it wins.
        assertThat(service.tryReserve(USER, 2)).isEqualTo(QuotaVerdict.GLOBAL_BUDGET_EXHAUSTED);
        verify(userUsage, never()).save(any());
    }

    // --- eBay closing the day ----------------------------------------------------------------

    @Test
    void anUpstreamQuotaReportIsPersistedSoADeployCannotLiftTheLockout() {
        when(globalUsage.findById(QUOTA_DATE)).thenReturn(Optional.of(usedGlobal(120)));

        service.recordExhaustedByUpstream("errorId 2001");

        final var saved = org.mockito.ArgumentCaptor.forClass(GlobalQuotaUsage.class);
        verify(globalUsage).save(saved.capture());
        assertThat(saved.getValue())
                .isNotNull()
                .extracting(GlobalQuotaUsage::isExhausted, GlobalQuotaUsage::exhaustedAt)
                .isEqualTo(List.of(true, NOW));
    }

    @Test
    void theFirstExhaustionReportWinsSoTheRecordedMomentStaysTheEarliest() {
        final var alreadyClosed = usedGlobal(120);
        alreadyClosed.markExhausted(NOW);
        when(globalUsage.findById(QUOTA_DATE)).thenReturn(Optional.of(alreadyClosed));
        when(timeService.now()).thenReturn(NOW, NOW.plusSeconds(600));

        service.recordExhaustedByUpstream("errorId 2001 again");

        assertThat(alreadyClosed.exhaustedAt()).isEqualTo(NOW);
    }

    @Test
    void theCurrentQuotaDayIsComputedInEbaysZoneNotOurs() {
        assertThat(service.currentQuotaDay().startingOn()).isEqualTo(QUOTA_DATE);
    }

    private static GlobalQuotaUsage usedGlobal(int calls) {
        final var usage = new GlobalQuotaUsage(new QuotaDay(QUOTA_DATE));
        usage.recordCalls(calls);
        return usage;
    }

    private static UserQuotaUsage usedByUser(int calls) {
        final var usage = new UserQuotaUsage(new QuotaDay(QUOTA_DATE), USER);
        usage.recordCalls(calls);
        return usage;
    }
}
