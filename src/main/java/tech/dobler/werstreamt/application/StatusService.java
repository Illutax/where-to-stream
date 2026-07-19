package tech.dobler.werstreamt.application;

import org.springframework.stereotype.Service;
import tech.dobler.werstreamt.application.dto.StatusDto;

import java.time.Instant;

/**
 * Exposes build/runtime status: the application version (from the JAR manifest) and the server
 * start time (captured once at bean creation).
 */
@Service
public class StatusService {

    private final Instant serverStart = Instant.now();

    public StatusDto status() {
        final var version = getClass().getPackage().getImplementationVersion();
        return new StatusDto(version, serverStart);
    }
}
