package tech.dobler.werstreamt.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.dobler.werstreamt.application.ListSelectionService;
import tech.dobler.werstreamt.application.dto.ChangeListResultDto;
import tech.dobler.werstreamt.application.dto.ListSelectionDto;

/** View / switch the active IMDb list. */
@RestController
@RequestMapping("/api/lists")
@RequiredArgsConstructor
public class ListApiController {

    private final ListSelectionService listSelectionService;

    @GetMapping
    public ListSelectionDto lists() {
        return listSelectionService.selection();
    }

    /** Switches the active list. Returns 400 (ProblemDetail) if the name is unknown. */
    @PutMapping("/selection")
    public ChangeListResultDto changeSelection(@RequestBody ChangeListRequest request) {
        return listSelectionService.changeTo(request.name());
    }
}
