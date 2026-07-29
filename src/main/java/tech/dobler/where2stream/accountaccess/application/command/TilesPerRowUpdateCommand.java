package tech.dobler.where2stream.accountaccess.application.command;

import tech.dobler.where2stream.shared.platform.api.ValidationException;

/** Update the current user's tiles-per-row preference (2-6) for the grid view. */
public record TilesPerRowUpdateCommand(String username, Integer tilesPerRow) {
    public TilesPerRowUpdateCommand {
        if (tilesPerRow == null) {
            throw new ValidationException("A tilesPerRow is required.");
        }
        if (tilesPerRow < 2 || tilesPerRow > 6) {
            throw new ValidationException("tilesPerRow must be between 2 and 6.");
        }
    }
}
