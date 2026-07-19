package tech.dobler.werstreamt.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tech.dobler.werstreamt.application.ListSelectionService;
import tech.dobler.werstreamt.application.UnknownListException;
import tech.dobler.werstreamt.services.CommonAttributeService;

@Controller
@RequiredArgsConstructor
public class ChangeListController {

    private final ListSelectionService listSelectionService;
    private final CommonAttributeService commonAttributeService;

    @GetMapping("/list")
    public String get(Model model)
    {
        final var selection = listSelectionService.selection();
        model.addAttribute("current", selection.current());
        model.addAttribute("availableLists", selection.available());
        commonAttributeService.add(model);
        return "change-list";
    }

    @PostMapping("/list-change")
    public String post(@RequestParam("list") String listName, RedirectAttributes attributes)
    {
        try {
            listSelectionService.changeTo(listName);
        } catch (UnknownListException e) {
            attributes.addAttribute("unknownEntry", e.listName());
            return "redirect:/list?error";
        }
        return "redirect:/list?success";
    }
}
