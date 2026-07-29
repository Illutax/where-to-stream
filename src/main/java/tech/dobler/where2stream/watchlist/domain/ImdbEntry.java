package tech.dobler.where2stream.watchlist.domain;

import lombok.NonNull;
import tech.dobler.where2stream.shared.domain.ImdbId;
import tech.dobler.where2stream.shared.domain.ReleaseYear;

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
