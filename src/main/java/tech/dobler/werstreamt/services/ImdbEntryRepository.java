package tech.dobler.werstreamt.services;

import lombok.extern.slf4j.Slf4j;
import tech.dobler.werstreamt.entities.ImdbEntry;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

@Slf4j
public class ImdbEntryRepository {
    private final Map<Integer, ImdbEntry> all = new HashMap<>();
    private final Map<Integer, ImdbEntry> unread = new HashMap<>();

    public void init(Collection<ImdbEntry> entries) {
        entries.forEach(e -> all.put(e.id(), e));
        entries.stream().filter(Predicate.not(ImdbEntry::isRated)).forEach(e -> unread.put(e.id(), e));

        log.info("Imported {} unread entries ({})", unread.size(), all.size());
    }

    public Optional<ImdbEntry> findById(int id) {
        return Optional.ofNullable(all.get(id));
    }
}
