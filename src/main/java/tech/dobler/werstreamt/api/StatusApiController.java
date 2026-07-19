package tech.dobler.werstreamt.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.dobler.werstreamt.application.StatusService;
import tech.dobler.werstreamt.application.dto.StatusDto;

/** Build/runtime status (version + server start time). */
@RestController
@RequestMapping("/api/status")
@RequiredArgsConstructor
public class StatusApiController {

    private final StatusService statusService;

    @GetMapping
    public StatusDto status() {
        return statusService.status();
    }
}
