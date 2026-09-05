package tech.dobler.where2stream.purchaseoffers.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * One eBay quota period, identified by the date it starts on.
 *
 * <p>Not a calendar day in our timezone: eBay's daily call allowance resets on eBay's schedule, and
 * counting against a different window means a freshly reset local counter can meet an allowance
 * that is still exhausted. The working hypothesis is 12 o'clock Pacific time — which comes from a
 * Gemini answer, is backed by no primary source, and is ambiguous between noon and midnight
 * (ADR-0017). Both the zone and the time are therefore configuration, not constants.
 *
 * <p>The zone is a {@link ZoneId}, never a fixed offset: Pacific time alternates between PST
 * (UTC−8) and PDT (UTC−7), and a hardcoded offset would be an hour wrong for part of the year —
 * in the direction that resets our counter while eBay's is still spent.
 */
public record QuotaDay(LocalDate startingOn) implements Comparable<QuotaDay> {

    public QuotaDay {
        Objects.requireNonNull(startingOn, "startingOn must not be null");
    }

    /**
     * The quota period the given instant falls into.
     *
     * <p>Before the reset time, the instant still belongs to the period that began the previous
     * day — that is what makes this different from simply taking the local date.
     */
    public static QuotaDay at(Instant now, ZoneId zone, LocalTime resetTime) {
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(zone, "zone must not be null");
        Objects.requireNonNull(resetTime, "resetTime must not be null");
        final var local = LocalDateTime.ofInstant(now, zone);
        final var date = local.toLocalTime().isBefore(resetTime)
                ? local.toLocalDate().minusDays(1)
                : local.toLocalDate();
        return new QuotaDay(date);
    }

    @Override
    public int compareTo(QuotaDay other) {
        return startingOn.compareTo(other.startingOn);
    }

    @Override
    public String toString() {
        return startingOn.toString();
    }
}
