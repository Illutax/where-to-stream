package tech.dobler.werstreamt.application.dto;

import java.util.List;

/**
 * Uniform shape for all five provider pages. Amazon populates both lists; the flatrate-only
 * providers (Disney+, Netflix, WOW) leave {@code paid} empty; Google Play leaves
 * {@code included} empty.
 *
 * @param provider the stable provider key (e.g. {@code "amazon"}, {@code "netflix"})
 */
public record ProviderPageDto(
        String provider,
        List<FlatrateEntryDto> included,
        List<PaidEntryDto> paid
) {
}
