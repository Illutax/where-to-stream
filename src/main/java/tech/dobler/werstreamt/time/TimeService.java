package tech.dobler.werstreamt.time;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Facade over the JVM wall clock. Production code reads "now" exclusively through this service
 * (never {@link Instant#now()} / {@link LocalDate#now()} directly) so that tests can substitute
 * a fixed clock and assert against exact, repeatable timestamps.
 */
public interface TimeService {

    /** The current instant (replaces {@link Instant#now()}). */
    Instant now();

    /** Today's date in the system zone (replaces {@link LocalDate#now()}). */
    LocalDate today();
}
