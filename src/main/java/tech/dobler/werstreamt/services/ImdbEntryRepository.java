package tech.dobler.werstreamt.services;

import lombok.extern.slf4j.Slf4j;
import tech.dobler.werstreamt.domain.ImdbEntry;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * In-memory store for the currently loaded IMDb list.
 *
 * <p>The whole state is held as an immutable snapshot behind an {@link AtomicReference}.
 * Reads are lock-free and always see a consistent snapshot, and a list change
 * ({@link #clear()} + {@link #init}) swaps the snapshot atomically — so concurrent readers
 * (e.g. the {@code parallelStream} pre-cache/refresh runs) never observe a half-populated
 * map.
 */
@Slf4j
public class ImdbEntryRepository {

    private record State(Map<Integer, ImdbEntry> byId, Map<String, ImdbEntry> byImdbId, String nameOfList) {
        private static final State EMPTY = new State(Map.of(), Map.of(), null);
    }

    private final AtomicReference<State> state = new AtomicReference<>(State.EMPTY);

    public ImdbEntryRepository() {
    }

    public void init(Collection<ImdbEntry> entries, String listName) {
        final var byId = new HashMap<Integer, ImdbEntry>();
        final var byImdbId = new HashMap<String, ImdbEntry>();
        entries.forEach(e -> {
            byId.put(e.id(), e);
            byImdbId.put(e.imdbId(), e);
        });
        state.set(new State(Map.copyOf(byId), Map.copyOf(byImdbId), listName));

        final var unseen = entries.stream().filter(Predicate.not(ImdbEntry::isRated)).count();
        log.info("Imported {} unseen entries ({})", unseen, byId.size());
    }

    public void clear() {
        state.set(State.EMPTY);
    }

    public Optional<ImdbEntry> findById(int id) {
        return Optional.ofNullable(state.get().byId().get(id));
    }

    public Optional<ImdbEntry> findByImdb(String imdbId) {
        return Optional.ofNullable(state.get().byImdbId().get(imdbId));
    }

    public List<ImdbEntry> findAll() {
        return state.get().byId().values().stream().toList();
    }

    public List<ImdbEntry> findAllSeen() {
        return state.get().byId().values().stream()
                .filter(ImdbEntry::isRated)
                .toList();
    }

    public String getNameOfList() {
        return Objects.requireNonNull(state.get().nameOfList());
    }
}
