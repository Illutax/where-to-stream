package tech.dobler.werstreamt.services;

import lombok.extern.slf4j.Slf4j;
import tech.dobler.werstreamt.entities.ImdbEntry;

import java.util.*;
import java.util.function.Predicate;

@Slf4j
public class ImdbEntryRepository {
    private final Map<Integer, ImdbEntry> all = new HashMap<>();
    private final Map<String, ImdbEntry> byID= new HashMap<>();

    public ImdbEntryRepository() {
    }

    public void init(Collection<ImdbEntry> entries) {
        entries.forEach(e -> all.put(e.id(), e));
        entries.forEach(e -> byID.put(e.imdbId(), e));
        final var unseen = new HashMap<>();
        entries.stream().filter(Predicate.not(ImdbEntry::isRated)).forEach(e -> unseen.put(e.id(), e));

        log.info("Imported {} unseen entries ({})", unseen.size(), all.size());
    }

    public void clear()
    {
        all.clear();
        byID.clear();
    }

    public Optional<ImdbEntry> findById(int id) {
        return Optional.ofNullable(all.get(id));
    }

    public Optional<ImdbEntry> findByImdb(String  imdbId) {
        return Optional.ofNullable(byID.get(imdbId));
    }

    public List<ImdbEntry> findAll() {
        return all.values().stream().toList();
    }
}
