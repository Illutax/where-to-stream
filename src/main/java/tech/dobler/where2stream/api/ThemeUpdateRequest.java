package tech.dobler.where2stream.api;

import tech.dobler.where2stream.domain.Theme;

/** Body of {@code PUT /api/me/theme}: the colour-scheme the current user selected. */
public record ThemeUpdateRequest(Theme theme) {
}
