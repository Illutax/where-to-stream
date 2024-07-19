package tech.dobler.werstreamt.entities;

import lombok.NonNull;

import java.net.URI;
import java.util.Comparator;

public record ImdbEntry(
        int id,
        String name,
        URI url,
        String added,
        boolean isRated,
        int year,
        String imdbId
) implements Comparable<ImdbEntry> {
    @Override
    public int compareTo(@NonNull ImdbEntry o) {
        return Comparator
                .comparing(ImdbEntry::added)
                .reversed()
                .compare(this, o);
    }
}
