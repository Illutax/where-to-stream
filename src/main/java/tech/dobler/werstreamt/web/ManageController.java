package tech.dobler.werstreamt.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tech.dobler.werstreamt.application.CacheManagementService;

import java.util.List;

/**
 * Cache-management UI: invalidate selected titles, and (re-)scrape only the titles whose
 * cache is missing or invalidated. All logic lives in {@link CacheManagementService}.
 */
@Controller
@RequiredArgsConstructor
public class ManageController {

    private final CacheManagementService cacheManagementService;
    private final CommonAttributeService commonAttributeService;

    @GetMapping("/manage")
    public String manage(Model model) {
        final var page = cacheManagementService.managePage();
        model.addAttribute("rows", page.rows());
        model.addAttribute("needsScrapeCount", page.needsScrapeCount());
        commonAttributeService.add(model);
        return "manage";
    }

    @PostMapping("/invalidate")
    public String invalidate(@RequestParam(name = "imdbIds", required = false) List<String> imdbIds,
                             RedirectAttributes attributes) {
        final int affected = cacheManagementService.invalidate(imdbIds).invalidated();
        attributes.addAttribute("invalidated", affected);
        return "redirect:/manage";
    }

    @PostMapping("/scrape-invalidated")
    public String scrapeInvalidated(RedirectAttributes attributes) {
        final int scraped = cacheManagementService.scrapeUncached().scraped();
        attributes.addAttribute("scraped", scraped);
        return "redirect:/manage";
    }
}
