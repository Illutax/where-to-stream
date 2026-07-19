package tech.dobler.werstreamt.application.dto;

/**
 * Result of switching the active list.
 *
 * @param selected the now-active list name
 * @param cached   how many entries were (pre-)cached as part of the switch
 */
public record ChangeListResultDto(
        String selected,
        int cached
) {
}
