package tech.dobler.werstreamt.application;

import org.springframework.stereotype.Service;
import tech.dobler.werstreamt.application.dto.StatusDto;
import tech.dobler.werstreamt.time.TimeService;

import java.time.Instant;

/**
 * Exposes build/runtime status: the application version (from the JAR manifest) and the server
 * start time (captured once at bean creation, via {@link TimeService}).
 */
@Service
public class StatusService {

    private final Instant serverStart;

    public StatusService(TimeService timeService) {
        this.serverStart = timeService.now();
    }

    public StatusDto status() {
        final var version = getClass().getPackage().getImplementationVersion();
        return new StatusDto(version, serverStart);
    }
}
