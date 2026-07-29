package tech.dobler.where2stream.watchlist.application.dto;

/**
 * Outcome of importing an IMDb CSV into a user's watchlist (a full sync: entries not in the upload
 * are removed).
 *
 * @param added   newly inserted titles
 * @param updated existing titles whose fields changed
 * @param removed titles that were on the list but not in the upload
 * @param total   total titles after the import
 */
public record WatchlistImportResultDto(
        int added,
        int updated,
        int removed,
        int total
) {
}
