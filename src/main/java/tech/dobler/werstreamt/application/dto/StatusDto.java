package tech.dobler.werstreamt.application.dto;

import java.time.Instant;

public record StatusDto(
        String version,
        Instant serverStart
) {
}
