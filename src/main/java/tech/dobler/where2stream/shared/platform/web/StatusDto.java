package tech.dobler.where2stream.shared.platform.web;

import java.time.Instant;

public record StatusDto(
        String version,
        Instant serverStart
) {
}
