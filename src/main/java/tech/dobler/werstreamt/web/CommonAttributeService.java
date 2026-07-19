package tech.dobler.werstreamt.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import tech.dobler.werstreamt.application.ListSelectionService;

/**
 * Adds attributes shared by all Thymeleaf pages (currently the selected list shown in the
 * navbar). Lives in the presentation layer — it only shapes the Thymeleaf {@link Model} — and
 * sources its data from the application layer, so no controller reaches into the services layer.
 */
@Component
@RequiredArgsConstructor
public class CommonAttributeService {
    private final ListSelectionService listSelectionService;

    public void add(Model model) {
        model.addAttribute("selectedList", listSelectionService.currentList());
    }
}
