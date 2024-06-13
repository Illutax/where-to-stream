package tech.dobler.werstreamt.entities;

import java.util.List;

public record QueryResult(String imdbId, String name, boolean flatrate, List<Availability> availabilities) {

    public boolean isAvailable() {
        return flatrate || !availabilities.isEmpty();
    }
}
