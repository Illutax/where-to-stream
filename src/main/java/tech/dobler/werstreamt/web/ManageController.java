package tech.dobler.werstreamt.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tech.dobler.werstreamt.domain.ImdbEntry;
import tech.dobler.werstreamt.services.CommonAttributeService;
import tech.dobler.werstreamt.services.ImdbCatalog;
import tech.dobler.werstreamt.services.PreCacheService;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cache-management UI: invalidate selected titles, and (re-)scrape only the titles whose
 * cache is missing or invalidated.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ManageController {

    private final ImdbCatalog imdbCatalog;
    private final PreCacheService preCacheService;
    private final CommonAttributeService commonAttributeService;

    /** One row of the manage table. {@code needsScrape} = currently missing/invalidated cache. */
    public record ManageRow(String imdbId, String name, boolean rated, boolean needsScrape) {
    }

    @GetMapping("/manage")
    public String manage(Model model) {
        final Set<String> needsScrape = preCacheService.findUncached().stream()
                .map(ImdbEntry::imdbId)
                .collect(Collectors.toSet());
        final List<ManageRow> rows = imdbCatalog.findAll().stream()
                .sorted(Comparator.comparing(ImdbEntry::name))
                .map(e -> new ManageRow(e.imdbId(), e.name(), e.isRated(), needsScrape.contains(e.imdbId())))
                .toList();
        model.addAttribute("rows", rows);
        model.addAttribute("needsScrapeCount", needsScrape.size());
        commonAttributeService.add(model);
        return "manage";
    }

    @PostMapping("/invalidate")
    public String invalidate(@RequestParam(name = "imdbIds", required = false) List<String> imdbIds,
                             RedirectAttributes attributes) {
        final var ids = imdbIds == null ? List.<String>of() : imdbIds;
        final int affected = preCacheService.invalidate(ids);
        attributes.addAttribute("invalidated", affected);
        return "redirect:/manage";
    }

    @PostMapping("/scrape-invalidated")
    public String scrapeInvalidated(RedirectAttributes attributes) {
        final int scraped = preCacheService.cacheUncached();
        attributes.addAttribute("scraped", scraped);
        return "redirect:/manage";
    }
}
