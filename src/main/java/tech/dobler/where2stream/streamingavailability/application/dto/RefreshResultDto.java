package tech.dobler.where2stream.streamingavailability.application.dto;

/**
 * @param refreshed titles actually re-scraped
 * @param failed    titles whose scrape failed and was skipped (the refresh continues past them;
 *                  their previous cache rows are untouched)
 */
public record RefreshResultDto(int refreshed, int failed) {
}
