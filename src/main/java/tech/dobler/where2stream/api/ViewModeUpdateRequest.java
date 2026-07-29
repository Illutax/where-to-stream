package tech.dobler.where2stream.api;

import tech.dobler.where2stream.domain.ViewMode;

/** Body of {@code PUT /api/me/view-mode}: the library layout the current user selected. */
public record ViewModeUpdateRequest(ViewMode viewMode) {
}
