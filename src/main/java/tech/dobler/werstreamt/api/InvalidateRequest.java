package tech.dobler.werstreamt.api;

import tech.dobler.werstreamt.domain.ImdbId;

import java.util.List;

/** Body of {@code POST /api/manage/invalidate}. */
public record InvalidateRequest(List<ImdbId> imdbIds) {
}
