package tech.dobler.werstreamt.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.werstreamt.application.dto.WatchlistImportResultDto;
import tech.dobler.werstreamt.domain.ImdbEntry;
import tech.dobler.werstreamt.persistence.WatchlistEntry;
import tech.dobler.werstreamt.persistence.WatchlistEntryRepository;
import tech.dobler.werstreamt.services.ExportReader;
import tech.dobler.werstreamt.time.TimeService;

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
    private CurrentUserService currentUserService;
    @Mock
    private TimeService timeService;

    private WatchlistImportService newService() {
        return new WatchlistImportService(repository, exportReader, currentUserService, timeService);
    }

    private static InputStream anyCsv() {
        return new ByteArrayInputStream("csv".getBytes(StandardCharsets.UTF_8));
    }

    private static ImdbEntry incoming(String imdbId, String name, boolean rated) {
        return new ImdbEntry(name, URI.create("https://www.imdb.com/title/" + imdbId + "/"),
                "2020-01-01", rated, 2020, imdbId);
    }

    private static WatchlistEntry stored(String imdbId, String name, boolean rated) {
        return WatchlistEntry.of(USER, imdbId, name, URI.create("https://www.imdb.com/title/" + imdbId + "/"),
                "2020-01-01", rated, 2020, NOW);
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

        assertThat(result.added()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.removed()).isEqualTo(1);
        assertThat(result.total()).isEqualTo(3);

        // tt4 inserted, tt2 mutated + saved, tt3 deleted, tt1 untouched (no save for an unchanged row).
        final ArgumentCaptor<WatchlistEntry> saved = ArgumentCaptor.captor();
        verify(repository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(WatchlistEntry::getImdbId)
                .containsExactlyInAnyOrder("tt2", "tt4");
        final ArgumentCaptor<WatchlistEntry> deleted = ArgumentCaptor.captor();
        verify(repository).delete(deleted.capture());
        assertThat(deleted.getValue().getImdbId()).isEqualTo("tt3");
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
    void duplicateImdbIdsInTheUploadCollapseToOneRowLastWins() {
        when(timeService.now()).thenReturn(NOW);
        when(exportReader.parse(any(InputStream.class))).thenReturn(List.of(
                incoming("tt1", "First", false),
                incoming("tt1", "Second", true)));
        when(repository.findByUserId(USER)).thenReturn(List.of());

        final var result = newService().importCsv(USER, anyCsv());

        assertThat(result.added()).isEqualTo(1);
        assertThat(result.total()).isEqualTo(1);
        final ArgumentCaptor<WatchlistEntry> saved = ArgumentCaptor.captor();
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Second");
        assertThat(saved.getValue().isRated()).isTrue();
    }

    @Test
    void statusReportsCountAndLastImport() {
        when(repository.countByUserId(USER)).thenReturn(5L);
        when(repository.findLastImportedAt(USER)).thenReturn(Optional.of(NOW));

        final var status = newService().status(USER);

        assertThat(status.count()).isEqualTo(5);
        assertThat(status.lastImportedAt()).isEqualTo(NOW);
    }

    @Test
    void clearDelegatesToRepository() {
        newService().clear(USER);

        verify(repository).deleteByUserId(USER);
    }

    @Test
    void resolveUserIdBridgesThroughCurrentUserService() {
        when(currentUserService.resolveId("alice")).thenReturn(USER);

        assertThat(newService().resolveUserId("alice")).isEqualTo(USER);
    }
}
