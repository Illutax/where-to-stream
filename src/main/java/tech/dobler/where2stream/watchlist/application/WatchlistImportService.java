package tech.dobler.where2stream.watchlist.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.dobler.where2stream.accountaccess.port.in.CurrentUserPort;
import tech.dobler.where2stream.watchlist.application.command.AddWatchlistEntryCommand;
import tech.dobler.where2stream.watchlist.application.command.MarkSeenCommand;
import tech.dobler.where2stream.watchlist.application.dto.WatchlistDto;
import tech.dobler.where2stream.watchlist.application.dto.WatchlistImportResultDto;
import tech.dobler.where2stream.watchlist.domain.ImdbEntry;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.shared.kernel.domain.ReleaseYear;
import tech.dobler.where2stream.watchlist.domain.InvalidImportException;
import tech.dobler.where2stream.watchlist.domain.NoSuchWatchlistEntryException;
import tech.dobler.where2stream.watchlist.domain.WatchlistDate;
import tech.dobler.where2stream.watchlist.domain.WatchlistEntry;
import tech.dobler.where2stream.watchlist.domain.WatchlistEntryAlreadyExistsException;
import tech.dobler.where2stream.watchlist.port.out.WatchlistEntryRepository;
import tech.dobler.where2stream.shared.platform.time.TimeService;

import java.io.InputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Manages a user's own watchlist: imports an IMDb CSV export (a full sync — add/update/remove),
 * reports status, and clears it.
 * Replaces the former global {@code ListSelectionService}.
 *
 * <p>Titles are resolved lazily against the shared cache on first page view, so the import stays a
 * fast, DB-only operation (no scraping while holding the transaction).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WatchlistImportService {

    private final WatchlistEntryRepository repository;
    private final ExportReader exportReader;
    private final CurrentUserPort currentUserPort;
    private final TimeService timeService;

    public WatchlistDto status(UUID userId) {
        return new WatchlistDto(repository.countByUserId(userId),
                repository.findLastImportedAt(userId).orElse(null));
    }

    /** Cheap count for the navbar (avoids loading rows). */
    public long count(UUID userId) {
        return repository.countByUserId(userId);
    }

    public UUID resolveUserId(String username) {
        return currentUserPort.resolveId(username);
    }

    /**
     * Toggle a single title's "seen" flag for the user (the lightweight in-app marking).
     * Throws {@link NoSuchWatchlistEntryException} when the title is not on the user's list.
     */
    @Transactional
    public void markSeen(MarkSeenCommand command) {
        final var entry = repository.findByUserIdAndImdbId(command.userId(), command.imdbId())
                .orElseThrow(() -> new NoSuchWatchlistEntryException(command.imdbId()));
        entry.markSeen(command.seen());
        repository.save(entry);
    }

    @Transactional
    public WatchlistImportResultDto importCsv(UUID userId, InputStream csv) {
        final var parsed = exportReader.parse(csv);
        if (parsed.isEmpty()) {
            throw new InvalidImportException("No valid entries found in the uploaded file.");
        }
        // De-duplicate the upload by imdbId (last row wins), preserving order.
        final Map<ImdbId, ImdbEntry> incoming = new LinkedHashMap<>();
        parsed.forEach(e -> incoming.put(e.imdbId(), e));

        final Map<ImdbId, WatchlistEntry> existing = repository.findByUserId(userId).stream()
                .collect(Collectors.toMap(WatchlistEntry::getImdbId, Function.identity()));

        final Instant now = timeService.now();
        int added = 0;
        int updated = 0;
        int removed = 0;

        for (ImdbEntry e : incoming.values()) {
            final WatchlistEntry current = existing.get(e.imdbId());
            if (current == null) {
                repository.save(WatchlistEntry.of(userId, e.imdbId(), e.name(), e.url(), e.added(),
                        e.isRated(), e.year(), now));
                added++;
            } else if (differs(current, e)) {
                current.update(e.name(), e.url(), e.added(), e.isRated(), e.year());
                repository.save(current);
                updated++;
            }
        }
        for (WatchlistEntry current : existing.values()) {
            if (!incoming.containsKey(current.getImdbId())) {
                repository.delete(current);
                removed++;
            }
        }

        log.info("Watchlist import for {}: +{} ~{} -{} ({} total)", userId, added, updated, removed, incoming.size());
        return new WatchlistImportResultDto(added, updated, removed, incoming.size());
    }

    @Transactional
    public void clear(UUID userId) {
        repository.deleteByUserId(userId);
    }

    /** Removes only the user's watched (seen) titles, leaving the rest of the watchlist untouched. */
    @Transactional
    public void clearSeen(UUID userId) {
        repository.deleteByUserIdAndRatedTrue(userId);
    }

    /**
     * Adds a single title found via search to the user's watchlist.
     * Throws {@link WatchlistEntryAlreadyExistsException} if it's already there.
     * {@code url} is left unset (null) — unlike a CSV import, there is no export-provided URL, and
     * the frontend already derives the canonical IMDb link from the id itself.
     */
    @Transactional
    public void addOne(AddWatchlistEntryCommand command) {
        if (repository.existsByUserIdAndImdbId(command.userId(), command.imdbId())) {
            throw new WatchlistEntryAlreadyExistsException(command.imdbId());
        }
        repository.save(WatchlistEntry.of(command.userId(), command.imdbId(), command.name(), null,
                WatchlistDate.of(timeService.today().toString()), false, ReleaseYear.of(command.year()),
                timeService.now()));
    }

    private static boolean differs(WatchlistEntry current, ImdbEntry incoming) {
        return current.isRated() != incoming.isRated()
                || !Objects.equals(current.getYear(), incoming.year())
                || !Objects.equals(current.getName(), incoming.name())
                || !Objects.equals(current.getAdded(), incoming.added())
                || !Objects.equals(current.getUrl(), incoming.url() == null ? null : incoming.url().toString());
    }
}
