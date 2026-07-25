package tech.dobler.werstreamt.api;

import tech.dobler.werstreamt.domain.Theme;

/** Body of {@code PUT /api/me/theme}: the colour-scheme the current user selected. */
public record ThemeUpdateRequest(Theme theme) {
}
