package tech.dobler.where2stream.accountaccess.adapter.in.api;

import tech.dobler.where2stream.accountaccess.domain.Language;

/** Body of {@code PUT /api/me/language} (null → 400). */
public record LanguageUpdateRequest(Language language) {
}
