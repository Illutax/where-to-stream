package tech.dobler.werstreamt.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tech.dobler.werstreamt.entities.ImdbEntry;
import tech.dobler.werstreamt.rest.PreCacheController;
import tech.dobler.werstreamt.services.ExportReader;
import tech.dobler.werstreamt.services.ImdbEntryRepository;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Transactional
@Controller
@RequiredArgsConstructor
@Slf4j
public class ChangeListController {

    private final ImdbEntryRepository imdbEntryRepository;
    private final ExportReader exportReader;
    private final PreCacheController cacheController;

    @GetMapping("/list")
    public String get(Model model)
    {
        model.addAttribute("availableLists", availableLists());
        return "change-list";
    }

    @PostMapping("/list-change")
    public String post(@RequestParam("list") String listName, RedirectAttributes attributes)
    {
        if (!availableLists().contains(listName))
        {
            attributes.addAttribute("unknownEntry", listName);
            return "redirect:/list?error";
        }

        log.info("Clearing imdbRepository...");
        imdbEntryRepository.clear();
        log.info("Reading new list {}", listName);
        List<ImdbEntry> entries = exportReader.parse(listName);
        log.info("Initializing with {} entries", entries.size());
        imdbEntryRepository.init(entries);
        cacheController.cache();
        return "redirect:/list?success";
    }

    private static List<String> availableLists()
    {
        Path assetsPath = Paths.get("assets");
        return Arrays.asList(Objects.requireNonNull(assetsPath.toFile().list()));
    }
}
