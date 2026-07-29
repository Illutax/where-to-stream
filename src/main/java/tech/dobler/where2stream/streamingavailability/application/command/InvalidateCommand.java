package tech.dobler.where2stream.streamingavailability.application.command;

import tech.dobler.where2stream.shared.kernel.domain.ImdbId;

import java.util.List;

/** Body of {@code POST /api/manage/invalidate}. A missing/null list is a no-op, not an error. */
public record InvalidateCommand(List<ImdbId> imdbIds) {
    public InvalidateCommand {
        imdbIds = imdbIds == null ? List.of() : imdbIds;
    }
}
