package tech.dobler.where2stream.shared.platform.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.dobler.where2stream.shared.platform.web.InstanceMetricsDto;
import tech.dobler.where2stream.shared.platform.web.InstanceMetricsService;

/**
 * Instance metrics for the ADMIN dashboard.
 *
 * <p>The path carries the authorisation: {@code SecurityConfig} maps {@code /api/admin/**} to
 * {@code hasRole("ADMIN")}, so this needs no annotation of its own — and equally, moving it out
 * from under {@code /api/admin} would silently make it readable by every logged-in user.
 * {@code SecurityRulesTest} pins both directions.
 */
@RestController
@RequestMapping("/api/admin/metrics")
@RequiredArgsConstructor
public class AdminMetricsApiController {

    private final InstanceMetricsService instanceMetricsService;

    @GetMapping
    public InstanceMetricsDto metrics() {
        return instanceMetricsService.metrics();
    }
}
