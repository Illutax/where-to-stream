package tech.dobler.where2stream.purchaseoffers.port.out;

import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import tech.dobler.where2stream.purchaseoffers.domain.GlobalQuotaUsage;
import tech.dobler.where2stream.purchaseoffers.domain.QuotaDay;
import tech.dobler.where2stream.purchaseoffers.domain.UserQuotaUsage;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Round-trips the quota tables against the embedded H2 database.
 *
 * <p>These are the parts of the quota work that no mock can vouch for: that the Liquibase schema
 * matches the entities, that the derived query name resolves to the columns we think it does, and
 * that the unique constraint really prevents a second row for the same user and day. The last one
 * matters — without it, a race could hand one user two counters and effectively double their
 * allowance.
 */
@DataJpaTest
class QuotaUsageRepositoryTest {

    private static final QuotaDay DAY = new QuotaDay(LocalDate.of(2026, 9, 5));
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @Autowired
    private GlobalQuotaUsageRepository globalUsage;
    @Autowired
    private UserQuotaUsageRepository userUsage;
    @Autowired
    private EntityManager entityManager;

    @Test
    void aGlobalCounterSurvivesARoundTripIncludingItsExhaustionMoment() {
        final var exhaustedAt = Instant.parse("2026-09-05T21:30:00Z");
        final var usage = new GlobalQuotaUsage(DAY);
        usage.recordCalls(42);
        usage.markExhausted(exhaustedAt);
        globalUsage.save(usage);
        entityManager.flush();
        entityManager.clear();

        assertThat(globalUsage.findById(DAY.startingOn())).get()
                .extracting(GlobalQuotaUsage::callsUsed, GlobalQuotaUsage::isExhausted,
                        GlobalQuotaUsage::exhaustedAt)
                .isEqualTo(List.of(42L, true, exhaustedAt));
    }

    @Test
    void aFreshGlobalCounterHasNoExhaustionMoment() {
        globalUsage.save(new GlobalQuotaUsage(DAY));
        entityManager.flush();
        entityManager.clear();

        assertThat(globalUsage.findById(DAY.startingOn())).get()
                .extracting(GlobalQuotaUsage::isExhausted, GlobalQuotaUsage::exhaustedAt)
                .isEqualTo(java.util.Arrays.asList(false, null));
    }

    @Test
    void theDerivedQueryFindsTheRightUsersCounterForTheRightDay() {
        final var aliceToday = new UserQuotaUsage(DAY, ALICE);
        aliceToday.recordCalls(4);
        final var bobToday = new UserQuotaUsage(DAY, BOB);
        bobToday.recordCalls(99);
        final var aliceYesterday = new UserQuotaUsage(new QuotaDay(DAY.startingOn().minusDays(1)), ALICE);
        aliceYesterday.recordCalls(7);
        userUsage.saveAll(List.of(aliceToday, bobToday, aliceYesterday));
        entityManager.flush();
        entityManager.clear();

        assertThat(userUsage.findByQuotaDayAndUserId(DAY.startingOn(), ALICE)).get()
                .extracting(UserQuotaUsage::userId, UserQuotaUsage::callsUsed)
                .isEqualTo(List.of(ALICE, 4L));
    }

    @Test
    void aUserWithoutACounterForThatDayHasNone() {
        assertThat(userUsage.findByQuotaDayAndUserId(DAY.startingOn(), ALICE)).isEmpty();
    }

    @Test
    void allCountersOfADayCanBeListedForTheRetrospectiveTheTicketWants() {
        userUsage.saveAll(List.of(new UserQuotaUsage(DAY, ALICE), new UserQuotaUsage(DAY, BOB)));
        entityManager.flush();
        entityManager.clear();

        assertThat(userUsage.findByQuotaDay(DAY.startingOn()))
                .extracting(UserQuotaUsage::userId)
                .containsExactlyInAnyOrder(ALICE, BOB);
    }

    @Test
    void aSecondCounterForTheSameUserAndDayIsRefusedByTheDatabase() {
        userUsage.save(new UserQuotaUsage(DAY, ALICE));
        entityManager.flush();

        // Two counters for one user and day would silently double that user's allowance.
        userUsage.save(new UserQuotaUsage(DAY, ALICE));

        // Hibernate's own exception, not Spring's DataIntegrityViolationException: the flush goes
        // through the EntityManager here, so nothing translates it. What matters is that the
        // database refuses the row, and it does.
        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> entityManager.flush())
                .withMessageContaining("UK_EBAY_USER_QUOTA_DAY");
    }
}
