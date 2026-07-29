package tech.dobler.where2stream.accountaccess.adapter.in.api;

/** Body of {@code PUT /api/me/show-german-title} (Boolean so a missing value is a 400). */
public record ShowGermanTitleUpdateRequest(Boolean showGermanTitle) {
}
