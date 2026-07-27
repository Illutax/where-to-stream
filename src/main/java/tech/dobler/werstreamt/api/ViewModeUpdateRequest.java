package tech.dobler.werstreamt.api;

import tech.dobler.werstreamt.domain.ViewMode;

/** Body of {@code PUT /api/me/view-mode}: the library layout the current user selected. */
public record ViewModeUpdateRequest(ViewMode viewMode) {
}
