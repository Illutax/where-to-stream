package tech.dobler.where2stream.shared.platform.web;

import org.springframework.stereotype.Service;
import tech.dobler.where2stream.shared.platform.web.StatusDto;
import tech.dobler.where2stream.shared.platform.time.TimeService;

import java.time.Instant;

/**
 * Exposes build/runtime status: the application version (from the JAR manifest)
 * and the server start time (captured once at bean creation, via {@link TimeService}).
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
