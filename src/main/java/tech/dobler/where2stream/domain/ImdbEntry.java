package tech.dobler.where2stream.domain;

import lombok.NonNull;

import java.net.URI;
import java.util.Comparator;

public record ImdbEntry(
        String name,
        URI url,
        WatchlistDate added,
        boolean isRated,
        ReleaseYear year,
        ImdbId imdbId
) implements Comparable<ImdbEntry> {
    @Override
    public int compareTo(@NonNull ImdbEntry o) {
        return Comparator
                .comparing(ImdbEntry::added)
                .reversed()
                .compare(this, o);
    }
}
