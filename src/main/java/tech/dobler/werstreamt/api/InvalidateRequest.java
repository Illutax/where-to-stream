package tech.dobler.werstreamt.api;

import java.util.List;

/** Body of {@code POST /api/manage/invalidate}. */
public record InvalidateRequest(List<String> imdbIds) {
}
