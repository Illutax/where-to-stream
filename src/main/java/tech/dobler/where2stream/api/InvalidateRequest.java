package tech.dobler.where2stream.api;

import tech.dobler.where2stream.domain.ImdbId;

import java.util.List;

/** Body of {@code POST /api/manage/invalidate}. */
public record InvalidateRequest(List<ImdbId> imdbIds) {
}
