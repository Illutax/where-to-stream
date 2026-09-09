package tech.dobler.where2stream.watchlist.application.dto;

/**
 * Outcome of importing an IMDb CSV into a user's watchlist (a full sync: entries not in the upload
 * are removed).
 *
 * @param added          newly inserted titles
 * @param updated        existing titles whose fields changed
 * @param removed        titles that were on the list but not in the upload — always {@code 0} when
 *                       {@code unreadableRows} is not zero, see below
 * @param total          total titles after the import
 * @param unreadableRows rows of the upload that could not be parsed and were skipped. When this is
 *                       not zero the removal half of the sync does not run at all (TODO-70), so the
 *                       client has to say so: the user asked for a full sync and got a merge, and
 *                       titles they deleted on IMDb are still on their list here.
 */
public record WatchlistImportResultDto(
        int added,
        int updated,
        int removed,
        int total,
        int unreadableRows
) {
}
