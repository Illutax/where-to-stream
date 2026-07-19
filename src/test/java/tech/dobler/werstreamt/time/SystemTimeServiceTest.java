package tech.dobler.werstreamt.time;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SystemTimeServiceTest {

    private final SystemTimeService service = new SystemTimeService();

    @Test
    void nowIsCloseToTheSystemClock() {
        final var before = Instant.now();
        final var now = service.now();
        final var after = Instant.now();

        assertThat(now).isBetween(before.minus(Duration.ofSeconds(1)), after.plus(Duration.ofSeconds(1)));
    }

    @Test
    void todayReturnsTheSystemDate() {
        assertThat(service.today()).isEqualTo(LocalDate.now());
    }
}
