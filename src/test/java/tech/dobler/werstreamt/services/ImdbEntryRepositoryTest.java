package tech.dobler.werstreamt.services;

import org.junit.jupiter.api.Test;
import tech.dobler.werstreamt.domain.ImdbEntry;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImdbEntryRepositoryTest {

    private final ImdbEntryRepository repo = new ImdbEntryRepository();

    private static ImdbEntry entry(int id, String name, String imdbId, boolean rated) {
        return new ImdbEntry(id, name, URI.create("https://www.imdb.com/title/" + imdbId + "/"),
                "2020-01-01", rated, 2020, imdbId);
    }

    @Test
    void initThenFind() {
        final var a = entry(1, "A", "tt1", true);
        final var b = entry(2, "B", "tt2", false);
        repo.init(List.of(a, b), "list-1");

        assertThat(repo.findById(1)).contains(a);
        assertThat(repo.findByImdb("tt2")).contains(b);
        assertThat(repo.findAll()).containsExactlyInAnyOrder(a, b);
        assertThat(repo.findAllSeen()).containsExactly(a);
        assertThat(repo.getNameOfList()).isEqualTo("list-1");
    }

    @Test
    void clearEmptiesStore() {
        repo.init(List.of(entry(1, "A", "tt1", true)), "l");

        repo.clear();

        assertThat(repo.findAll()).isEmpty();
        assertThat(repo.findById(1)).isEmpty();
    }

    @Test
    void initReplacesPreviousSnapshotAtomically() {
        repo.init(List.of(entry(1, "A", "tt1", true)), "first");
        repo.init(List.of(entry(2, "B", "tt2", false)), "second");

        assertThat(repo.findById(1)).isEmpty();
        assertThat(repo.findByImdb("tt2")).isPresent();
        assertThat(repo.getNameOfList()).isEqualTo("second");
    }

    @Test
    void findByMissingId() {
        repo.init(List.of(), "empty");

        assertThat(repo.findById(99)).isEmpty();
    }
}
