package tech.dobler.where2stream.shared.platform.web;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StatusDto} is served unauthenticated at {@code /public/status}, so every field in it is
 * published to anyone who asks.
 *
 * <p>This test exists because that is easy to forget and impossible to notice: adding a field to a
 * record is a one-line change, the compiler is happy, the SPA renders it, and nothing anywhere says
 * the value just became public. {@code titles} is here by decision (TODO-71) — a size-of-instance
 * figure with nothing personal in it. The user count is deliberately <em>not</em>, and lives in
 * {@link InstanceMetricsDto} behind {@code /api/admin/**}.
 *
 * <p>So this fails on any change to the shape rather than on a list of forbidden names: a
 * blocklist only catches the fields somebody already thought of.
 */
class PublicStatusIsMinimalTest {

    @Test
    void theUnauthenticatedStatusPayloadCarriesOnlyTheseThreeFields() {
        final var fields = Arrays.stream(StatusDto.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(fields)
                .as("StatusDto is public at /public/status — a new field here is a new field on the "
                        + "open internet. If that is intended, say so and change this list; if it is "
                        + "operator data, it belongs in InstanceMetricsDto behind /api/admin/**")
                .isEqualTo(List.of("version", "serverStart", "titles"));
    }
}
