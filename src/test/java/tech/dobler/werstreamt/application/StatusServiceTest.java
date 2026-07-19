package tech.dobler.werstreamt.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.werstreamt.time.TimeService;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatusServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Mock
    private TimeService timeService;

    @Test
    void reportsServerStartFromTheClockAtConstruction() {
        when(timeService.now()).thenReturn(NOW);

        final var service = new StatusService(timeService);

        assertThat(service.status().serverStart()).isEqualTo(NOW);
    }

    @Test
    void capturesServerStartOnceAtConstructionNotPerCall() {
        when(timeService.now()).thenReturn(NOW);
        final var service = new StatusService(timeService);

        final var first = service.status();
        final var second = service.status();

        assertThat(first.serverStart()).isEqualTo(second.serverStart());
        // the clock is read exactly once (at construction), never again per status() call
        verify(timeService, times(1)).now();
    }
}
