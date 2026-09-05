package tech.dobler.where2stream.purchaseoffers.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The daylight-saving cases are the point of this class.
 * A quota day computed from a fixed UTC offset would be an hour wrong for part of the year, and
 * because the error only appears twice a year and only for one hour, nothing else would notice.
 */
class QuotaDayTest {

    private static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");
    private static final LocalTime MIDNIGHT = LocalTime.MIDNIGHT;
    private static final LocalTime NOON = LocalTime.NOON;

    @Test
    void anInstantAfterTheResetBelongsToThatDay() {
        // 2026-01-15 08:00 UTC is 2026-01-15 00:00 Pacific standard time (UTC-8) — exactly the reset.
        final var day = QuotaDay.at(Instant.parse("2026-01-15T08:00:00Z"), PACIFIC, MIDNIGHT);

        assertThat(day.startingOn()).isEqualTo(LocalDate.of(2026, 1, 15));
    }

    @Test
    void anInstantBeforeTheResetStillBelongsToThePreviousDay() {
        // One minute earlier is still 2026-01-14 in Pacific time.
        final var day = QuotaDay.at(Instant.parse("2026-01-15T07:59:00Z"), PACIFIC, MIDNIGHT);

        assertThat(day.startingOn()).isEqualTo(LocalDate.of(2026, 1, 14));
    }

    @Test
    void inWinterTheBoundarySitsAtEightUtcBecausePacificIsUtcMinusEight() {
        final var justBefore = QuotaDay.at(Instant.parse("2026-01-15T07:59:59Z"), PACIFIC, MIDNIGHT);
        final var justAfter = QuotaDay.at(Instant.parse("2026-01-15T08:00:00Z"), PACIFIC, MIDNIGHT);

        assertThat(justBefore.startingOn()).isEqualTo(LocalDate.of(2026, 1, 14));
        assertThat(justAfter.startingOn()).isEqualTo(LocalDate.of(2026, 1, 15));
    }

    @Test
    void inSummerTheBoundaryMovesToSevenUtcBecausePacificIsUtcMinusSeven() {
        // The same wall-clock reset, an hour earlier in UTC. A hardcoded UTC-8 would place the
        // boundary at 08:00 here and roll the day over an hour late for half the year.
        final var justBefore = QuotaDay.at(Instant.parse("2026-07-15T06:59:59Z"), PACIFIC, MIDNIGHT);
        final var justAfter = QuotaDay.at(Instant.parse("2026-07-15T07:00:00Z"), PACIFIC, MIDNIGHT);

        assertThat(justBefore.startingOn()).isEqualTo(LocalDate.of(2026, 7, 14));
        assertThat(justAfter.startingOn()).isEqualTo(LocalDate.of(2026, 7, 15));
    }

    @Test
    void theHypothesisCanAlsoBeReadAsNoonWithoutChangingCode() {
        // "12 o'clock Pacific" is ambiguous, so the reset time is configuration. At noon, a morning
        // instant belongs to the day before.
        final var morning = QuotaDay.at(Instant.parse("2026-01-15T17:00:00Z"), PACIFIC, NOON); // 09:00 PST
        final var afternoon = QuotaDay.at(Instant.parse("2026-01-15T21:00:00Z"), PACIFIC, NOON); // 13:00 PST

        assertThat(morning.startingOn()).isEqualTo(LocalDate.of(2026, 1, 14));
        assertThat(afternoon.startingOn()).isEqualTo(LocalDate.of(2026, 1, 15));
    }

    @Test
    void quotaDaysOrderChronologically() {
        final var earlier = new QuotaDay(LocalDate.of(2026, 1, 14));
        final var later = new QuotaDay(LocalDate.of(2026, 1, 15));

        assertThat(earlier).isLessThan(later);
    }

    @Test
    void theStringFormIsTheDateItself() {
        assertThat(new QuotaDay(LocalDate.of(2026, 1, 15))).hasToString("2026-01-15");
    }
}
