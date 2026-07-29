package tech.dobler.where2stream.streamingavailability.application.dto;

import java.util.List;

/**
 * Uniform shape for all five provider pages.
 * Amazon populates both lists; the flatrate-only providers (Disney+, Netflix, WOW) leave
 * {@code paid} empty; YouTube Store leaves {@code included} empty.
 *
 * @param provider the stable provider key (e.g. {@code "amazon"}, {@code "netflix"})
 */
public record ProviderPageDto(
        String provider,
        List<FlatrateEntryDto> included,
        List<PaidEntryDto> paid
) {
}
