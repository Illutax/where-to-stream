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
import tech.dobler.werstreamt.services.CommonAttributeService;
import tech.dobler.werstreamt.services.ExportReader;
import tech.dobler.werstreamt.services.FileUtils;
import tech.dobler.werstreamt.services.ImdbEntryRepository;

import java.util.List;

@Transactional
@Controller
@RequiredArgsConstructor
@Slf4j
public class ChangeListController {

    private final ImdbEntryRepository imdbEntryRepository;
    private final ExportReader exportReader;
    private final PreCacheController cacheController;
    private final CommonAttributeService commonAttributeService;
    private final FileUtils fileUtils;

    @GetMapping("/list")
    public String get(Model model)
    {
        model.addAttribute("current", imdbEntryRepository.getNameOfList());
        model.addAttribute("availableLists", fileUtils.availableLists());
        commonAttributeService.add(model);
        return "change-list";
    }

    @PostMapping("/list-change")
    public String post(@RequestParam("list") String listName, RedirectAttributes attributes)
    {
        if (!fileUtils.availableLists().contains(listName))
        {
            attributes.addAttribute("unknownEntry", listName);
            return "redirect:/list?error";
        }

        log.info("Clearing imdbRepository...");
        imdbEntryRepository.clear();
        log.info("Reading new list {}", listName);
        List<ImdbEntry> entries = exportReader.parse(listName);
        log.info("Initializing with {} entries", entries.size());
        imdbEntryRepository.init(entries, listName);
        cacheController.cache();
        return "redirect:/list?success";
    }
}
