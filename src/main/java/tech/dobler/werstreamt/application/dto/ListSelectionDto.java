package tech.dobler.werstreamt.application.dto;

import java.util.List;

/**
 * The currently selected IMDb list plus the lists available to switch to.
 *
 * @param current   the name of the active list
 * @param available all list file names found in the assets directory
 */
public record ListSelectionDto(
        String current,
        List<String> available
) {
}
