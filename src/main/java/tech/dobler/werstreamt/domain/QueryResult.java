package tech.dobler.werstreamt.domain;

import java.util.List;

public record QueryResult(
        String imdbId,
        String streamingServiceName,
        boolean flatrate,
        List<Availability> availabilities,
        String languages
) {

    public boolean isAvailable() {
        return flatrate || !availabilities.isEmpty();
    }

    /** Display name including the language differentiator, e.g. {@code "Prime Video (Deutsch)"}. */
    public String label() {
        return languages == null || languages.isBlank()
                ? streamingServiceName
                : "%s (%s)".formatted(streamingServiceName, languages);
    }
}
