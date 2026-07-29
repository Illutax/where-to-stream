package tech.dobler.where2stream.shared.platform.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.dobler.where2stream.shared.platform.web.StatusService;
import tech.dobler.where2stream.shared.platform.web.StatusDto;

/**
 * Public health/status probe (version + server start time) as JSON.
 * Unauthenticated on purpose (matched by {@code /public/**} in the security config)
 * so external monitoring can reach it; the authenticated SPA reads the same data from
 * {@code /api/status}.
 */
@RestController
@RequiredArgsConstructor
public class StatusController {

    private final StatusService statusService;

    @GetMapping("/public/status")
    public StatusDto status() {
        return statusService.status();
    }
}
