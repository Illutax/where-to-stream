package tech.dobler.where2stream.shared.platform.web;

import java.time.Instant;

/**
 * Build/runtime status, served both to the SPA and — by {@code StatusController} — to anyone at
 * {@code /public/status}.
 *
 * <p><strong>Everything in this record is public.</strong> {@code titles} is here by decision: it
 * says how large the instance is without saying anything about anyone, and on the monitoring probe
 * it is the field that shows the deployment still has its data. Nothing else belongs here —
 * operator figures, above all the user count, live in {@link InstanceMetricsDto} behind
 * {@code /api/admin/**}. {@code PublicStatusIsMinimalTest} fails if this record grows.
 *
 * @param version     application version from the JAR manifest, null in a dev run
 * @param serverStart when this instance came up
 * @param titles      distinct titles tracked; cached for a few minutes, see {@link StatusService}
 */
public record StatusDto(
        String version,
        Instant serverStart,
        long titles
) {
}
