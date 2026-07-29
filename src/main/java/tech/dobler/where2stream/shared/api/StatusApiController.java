package tech.dobler.where2stream.shared.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.dobler.where2stream.shared.web.StatusService;
import tech.dobler.where2stream.shared.web.StatusDto;

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
