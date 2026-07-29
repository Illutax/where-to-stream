package tech.dobler.where2stream.accountaccess.adapter.in.web;

/** Body of {@code PUT /api/me/show-age-ratings} (Boolean so a missing value is a 400, not a default). */
public record ShowAgeRatingsUpdateRequest(Boolean showAgeRatings) {
}
