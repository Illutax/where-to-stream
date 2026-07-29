package tech.dobler.where2stream.application.dto;

import java.time.Instant;

public record StatusDto(
        String version,
        Instant serverStart
) {
}
