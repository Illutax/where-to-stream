package tech.dobler.where2stream.api;

import tech.dobler.where2stream.domain.Language;

/** Body of {@code PUT /api/me/language} (null → 400). */
public record LanguageUpdateRequest(Language language) {
}
