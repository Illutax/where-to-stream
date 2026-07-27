package tech.dobler.werstreamt.api;

import tech.dobler.werstreamt.domain.Language;

/** Body of {@code PUT /api/me/language} (null → 400). */
public record LanguageUpdateRequest(Language language) {
}
