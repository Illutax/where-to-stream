package tech.dobler.werstreamt.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.dobler.werstreamt.application.dto.ChangeListResultDto;
import tech.dobler.werstreamt.application.dto.ListSelectionDto;
import tech.dobler.werstreamt.domain.ImdbEntry;
import tech.dobler.werstreamt.services.ExportReader;
import tech.dobler.werstreamt.services.FileUtils;
import tech.dobler.werstreamt.services.ImdbCatalog;
import tech.dobler.werstreamt.services.PreCacheService;

import java.util.List;

/**
 * Reads and switches the active IMDb list. The switch orchestration (validate → clear → parse
 * → re-init → pre-cache) previously lived in {@code ChangeListController}; it is here so both
 * the Thymeleaf form and the {@code /api/lists} endpoint drive the identical logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ListSelectionService {

    private final ImdbCatalog imdbCatalog;
    private final ExportReader exportReader;
    private final PreCacheService preCacheService;
    private final FileUtils fileUtils;

    public ListSelectionDto selection() {
        return new ListSelectionDto(imdbCatalog.getNameOfList(), fileUtils.availableLists());
    }

    /**
     * Switches the active list to {@code listName}, repopulating the catalogue and cache.
     *
     * @throws UnknownListException if {@code listName} is not among the available asset files
     */
    public ChangeListResultDto changeTo(String listName) {
        if (!fileUtils.availableLists().contains(listName)) {
            throw new UnknownListException(listName);
        }

        log.info("Clearing imdbRepository...");
        imdbCatalog.clear();
        log.info("Reading new list {}", listName);
        final List<ImdbEntry> entries = exportReader.parse(listName);
        log.info("Initializing with {} entries", entries.size());
        imdbCatalog.init(entries, listName);
        final int cached = preCacheService.cacheAll();
        return new ChangeListResultDto(listName, cached);
    }
}
