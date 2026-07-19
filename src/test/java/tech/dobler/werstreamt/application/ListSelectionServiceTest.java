package tech.dobler.werstreamt.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.werstreamt.domain.ImdbEntry;
import tech.dobler.werstreamt.services.ExportReader;
import tech.dobler.werstreamt.services.FileUtils;
import tech.dobler.werstreamt.services.ImdbCatalog;
import tech.dobler.werstreamt.services.PreCacheService;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListSelectionServiceTest {

    @Mock
    private ImdbCatalog imdbCatalog;
    @Mock
    private ExportReader exportReader;
    @Mock
    private PreCacheService preCacheService;
    @Mock
    private FileUtils fileUtils;
    @InjectMocks
    private ListSelectionService service;

    @Test
    void selectionReportsCurrentAndAvailable() {
        when(imdbCatalog.getNameOfList()).thenReturn("current-list");
        when(fileUtils.availableLists()).thenReturn(List.of("a", "current-list", "b"));

        final var selection = service.selection();

        assertThat(selection.current()).isEqualTo("current-list");
        assertThat(selection.available()).containsExactly("a", "current-list", "b");
    }

    @Test
    void changeToUnknownListThrowsAndTouchesNothing() {
        when(fileUtils.availableLists()).thenReturn(List.of("known"));

        assertThatThrownBy(() -> service.changeTo("unknown"))
                .isInstanceOf(UnknownListException.class)
                .satisfies(e -> assertThat(((UnknownListException) e).listName()).isEqualTo("unknown"));

        verifyNoInteractions(exportReader, preCacheService);
    }

    @Test
    void changeToClearsParsesInitsAndCachesInOrder() {
        final var entries = List.of(new ImdbEntry(1, "n", URI.create("https://www.imdb.com/title/tt1/"),
                "2020-01-01", false, 2020, "tt1"));
        when(fileUtils.availableLists()).thenReturn(List.of("target"));
        when(exportReader.parse("target")).thenReturn(entries);
        when(preCacheService.cacheAll()).thenReturn(1);

        final var result = service.changeTo("target");

        assertThat(result.selected()).isEqualTo("target");
        assertThat(result.cached()).isEqualTo(1);

        final InOrder inOrder = inOrder(imdbCatalog, exportReader, preCacheService);
        inOrder.verify(imdbCatalog).clear();
        inOrder.verify(exportReader).parse("target");
        inOrder.verify(imdbCatalog).init(entries, "target");
        inOrder.verify(preCacheService).cacheAll();
    }
}
