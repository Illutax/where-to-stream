package tech.dobler.werstreamt.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class StatusServiceTest {

    @Test
    void statusCapturesServerStartOnceAndIsStable() {
        final var before = Instant.now();
        final var service = new StatusService();

        final var first = service.status();
        final var second = service.status();

        // server start is captured at construction and does not move between calls
        assertThat(first.serverStart()).isEqualTo(second.serverStart());
        assertThat(first.serverStart()).isAfterOrEqualTo(before);
        // version comes from the JAR manifest; null when running from classes (tests)
        assertThat(first.version()).isEqualTo(second.version());
    }
}
