package tech.dobler.where2stream.watchlist.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.where2stream.accountaccess.port.in.CurrentUserPort;
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchlistImportServiceTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private WatchlistEntryRepository repository;
    @Mock
    private ExportReader exportReader;
    @Mock
    private CurrentUserPort currentUserPort;
    @Mock
    private TimeService timeService;

    private WatchlistImportService newService() {
        return new WatchlistImportService(repository, exportReader, currentUserPort, timeService);
    }

    private static InputStream anyCsv() {
        return new ByteArrayInputStream("csv".getBytes(StandardCharsets.UTF_8));
    }

    private static ImdbId id(String imdbId) {
        return ImdbId.of(imdbId);
    }

    private static ImdbEntry incoming(String imdbId, String name, boolean rated) {
        return new ImdbEntry(name, URI.create("https://www.imdb.com/title/" + imdbId + "/"),
                WatchlistDate.of("2020-01-01"), rated, ReleaseYear.of(2020), id(imdbId));
    }

    private static WatchlistEntry stored(String imdbId, String name, boolean rated) {
        return WatchlistEntry.of(USER, id(imdbId), name, URI.create("https://www.imdb.com/title/" + imdbId + "/"),
                WatchlistDate.of("2020-01-01"), rated, ReleaseYear.of(2020), NOW);
    }

    @Test
    void importIsAFullSyncAddingUpdatingAndRemoving() {
        when(timeService.now()).thenReturn(NOW);
        // Upload: tt1 (unchanged), tt2 (renamed), tt4 (new). Existing tt3 is absent -> removed.
        when(exportReader.parse(any(InputStream.class))).thenReturn(List.of(
                incoming("tt1", "Same", false),
                incoming("tt2", "Renamed", false),
                incoming("tt4", "Brand New", true)));
        when(repository.findByUserId(USER)).thenReturn(List.of(
                stored("tt1", "Same", false),
                stored("tt2", "Old Name", false),
                stored("tt3", "Gone", false)));

        final WatchlistImportResultDto result = newService().importCsv(USER, anyCsv());

        assertThat(result)
                .extracting(WatchlistImportResultDto::added, WatchlistImportResultDto::updated,
                        WatchlistImportResultDto::removed, WatchlistImportResultDto::total)
                .containsExactly(1, 1, 1, 3);

        // tt4 inserted, tt2 mutated + saved, tt3 deleted, tt1 untouched (no save for an unchanged row).
        final ArgumentCaptor<WatchlistEntry> saved = ArgumentCaptor.captor();
        verify(repository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(WatchlistEntry::getImdbId)
                .containsExactlyInAnyOrder(id("tt2"), id("tt4"));
        final ArgumentCaptor<WatchlistEntry> deleted = ArgumentCaptor.captor();
        verify(repository).delete(deleted.capture());
        assertThat(deleted.getValue().getImdbId()).isEqualTo(id("tt3"));
    }

    @Test
    void emptyUploadIsRejectedAndTouchesNothing() {
        when(exportReader.parse(any(InputStream.class))).thenReturn(List.of());

        final var service = newService();
        final var csv = anyCsv();
        assertThatThrownBy(() -> service.importCsv(USER, csv))
                .isInstanceOf(InvalidImportException.class);

        verify(repository, never()).save(any());
        verify(repository, never()).delete(any());
    }

    @Test
    void reimportUpdatesWhenOnlyTheRatedFlagDiffers() {
        when(timeService.now()).thenReturn(NOW);
        when(exportReader.parse(any(InputStream.class))).thenReturn(List.of(incoming("tt1", "Same", true)));
        when(repository.findByUserId(USER)).thenReturn(List.of(stored("tt1", "Same", false)));

        final var result = newService().importCsv(USER, anyCsv());

        assertThat(result.updated()).isEqualTo(1);
    }

    @Test
    void reimportUpdatesWhenOnlyTheYearDiffers() {
        when(timeService.now()).thenReturn(NOW);
        final var differentYear = new ImdbEntry("Same", URI.create("https://www.imdb.com/title/tt1/"),
                WatchlistDate.of("2020-01-01"), false, ReleaseYear.of(1999), id("tt1"));
        when(exportReader.parse(any(InputStream.class))).thenReturn(List.of(differentYear));
        when(repository.findByUserId(USER)).thenReturn(List.of(stored("tt1", "Same", false)));

        final var result = newService().importCsv(USER, anyCsv());

        assertThat(result.updated()).isEqualTo(1);
    }

    @Test
    void reimportUpdatesWhenOnlyTheAddedDateDiffers() {
        when(timeService.now()).thenReturn(NOW);
        final var differentAdded = new ImdbEntry("Same", URI.create("https://www.imdb.com/title/tt1/"),
                WatchlistDate.of("2021-06-15"), false, ReleaseYear.of(2020), id("tt1"));
        when(exportReader.parse(any(InputStream.class))).thenReturn(List.of(differentAdded));
        when(repository.findByUserId(USER)).thenReturn(List.of(stored("tt1", "Same", false)));

        final var result = newService().importCsv(USER, anyCsv());

        assertThat(result.updated()).isEqualTo(1);
    }

    @Test
    void reimportUpdatesWhenOnlyTheUrlDiffersToNull() {
        when(timeService.now()).thenReturn(NOW);
        // ImdbEntry with no url at all (differs() must still compare it against the stored, non-null url).
        final var noUrl = new ImdbEntry("Same", null, WatchlistDate.of("2020-01-01"), false, ReleaseYear.of(2020), id("tt1"));
        when(exportReader.parse(any(InputStream.class))).thenReturn(List.of(noUrl));
        when(repository.findByUserId(USER)).thenReturn(List.of(stored("tt1", "Same", false)));

        final var result = newService().importCsv(USER, anyCsv());

        assertThat(result.updated()).isEqualTo(1);
        final ArgumentCaptor<WatchlistEntry> saved = ArgumentCaptor.captor();
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUrl()).isNull();
    }

    @Test
    void reimportSkipsAnUnchangedEntryEvenWithoutAUrl() {
        when(timeService.now()).thenReturn(NOW);
        final var noUrlIncoming = new ImdbEntry("Same", null, WatchlistDate.of("2020-01-01"), false, ReleaseYear.of(2020), id("tt1"));
        final var noUrlStored = WatchlistEntry.of(USER, id("tt1"), "Same", null,
                WatchlistDate.of("2020-01-01"), false, ReleaseYear.of(2020), NOW);
        when(exportReader.parse(any(InputStream.class))).thenReturn(List.of(noUrlIncoming));
        when(repository.findByUserId(USER)).thenReturn(List.of(noUrlStored));

        final var result = newService().importCsv(USER, anyCsv());

        assertThat(result.updated()).isZero();
        verify(repository, never()).save(any());
    }

    @Test
    void duplicateImdbIdsInTheUploadCollapseToOneRowLastWins() {
        when(timeService.now()).thenReturn(NOW);
        when(exportReader.parse(any(InputStream.class))).thenReturn(List.of(
                incoming("tt1", "First", false),
                incoming("tt1", "Second", true)));
        when(repository.findByUserId(USER)).thenReturn(List.of());

        final var result = newService().importCsv(USER, anyCsv());

        assertThat(result).extracting(WatchlistImportResultDto::added, WatchlistImportResultDto::total)
                .containsExactly(1, 1);
        final ArgumentCaptor<WatchlistEntry> saved = ArgumentCaptor.captor();
        verify(repository).save(saved.capture());
        assertThat(saved.getValue()).extracting(WatchlistEntry::getName, WatchlistEntry::isRated)
                .containsExactly("Second", true);
    }

    @Test
    void countDelegatesToRepository() {
        when(repository.countByUserId(USER)).thenReturn(3L);

        assertThat(newService().count(USER)).isEqualTo(3L);
    }

    @Test
    void statusReportsCountAndLastImport() {
        when(repository.countByUserId(USER)).thenReturn(5L);
        when(repository.findLastImportedAt(USER)).thenReturn(Optional.of(NOW));

        final var status = newService().status(USER);

        assertThat(status).extracting(WatchlistDto::count, WatchlistDto::lastImportedAt).containsExactly(5L, NOW);
    }

    @Test
    void clearDelegatesToRepository() {
        newService().clear(USER);

        verify(repository).deleteByUserId(USER);
    }

    @Test
    void clearSeenDelegatesToRepositoryDeleteByUserIdAndRatedTrue() {
        newService().clearSeen(USER);

        verify(repository).deleteByUserIdAndRatedTrue(USER);
    }

    @Test
    void addOnePersistsANewEntryWithTodaysDateAndUnrated() {
        when(timeService.now()).thenReturn(NOW);
        when(timeService.today()).thenReturn(java.time.LocalDate.parse("2026-01-01"));
        when(repository.existsByUserIdAndImdbId(USER, id("tt1"))).thenReturn(false);

        newService().addOne(USER, id("tt1"), "The Matrix", ReleaseYear.of(1999));

        final ArgumentCaptor<WatchlistEntry> saved = ArgumentCaptor.captor();
        verify(repository).save(saved.capture());
        assertThat(saved.getValue())
                .extracting(WatchlistEntry::getImdbId, WatchlistEntry::getName, WatchlistEntry::getYear,
                        WatchlistEntry::isRated, WatchlistEntry::getAdded)
                .containsExactly(id("tt1"), "The Matrix", ReleaseYear.of(1999), false, WatchlistDate.of("2026-01-01"));
    }

    @Test
    void addOneThrowsWhenAlreadyOnTheWatchlist() {
        when(repository.existsByUserIdAndImdbId(USER, id("tt1"))).thenReturn(true);

        final var service = newService();
        assertThatThrownBy(() -> service.addOne(USER, id("tt1"), "The Matrix", ReleaseYear.of(1999)))
                .isInstanceOf(WatchlistEntryAlreadyExistsException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void resolveUserIdBridgesThroughCurrentUserPort() {
        when(currentUserPort.resolveId("alice")).thenReturn(USER);

        assertThat(newService().resolveUserId("alice")).isEqualTo(USER);
    }

    @Test
    void markSeenFlipsTheFlagAndSaves() {
        final var entry = stored("tt1", "The Prestige", false);
        when(repository.findByUserIdAndImdbId(USER, id("tt1"))).thenReturn(Optional.of(entry));

        newService().markSeen(USER, id("tt1"), true);

        assertThat(entry.isRated()).isTrue();
        verify(repository).save(entry);
    }

    @Test
    void markSeenCanUnsetTheFlag() {
        final var entry = stored("tt1", "The Prestige", true);
        when(repository.findByUserIdAndImdbId(USER, id("tt1"))).thenReturn(Optional.of(entry));

        newService().markSeen(USER, id("tt1"), false);

        assertThat(entry.isRated()).isFalse();
        verify(repository).save(entry);
    }

    @Test
    void markSeenThrowsWhenTheTitleIsNotOnTheList() {
        when(repository.findByUserIdAndImdbId(USER, id("tt9"))).thenReturn(Optional.empty());

        final var service = newService();
        assertThatThrownBy(() -> service.markSeen(USER, id("tt9"), true))
                .isInstanceOf(NoSuchWatchlistEntryException.class);
        verify(repository, never()).save(any());
    }
}
