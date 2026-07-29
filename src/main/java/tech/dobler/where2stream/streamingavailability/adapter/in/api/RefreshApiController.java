package tech.dobler.where2stream.streamingavailability.adapter.in.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.dobler.where2stream.streamingavailability.application.RefreshService;
import tech.dobler.where2stream.streamingavailability.application.dto.RefreshResultDto;

/** Force-refresh cached availability for all titles or only the seen ones. */
@RestController
@RequestMapping("/api/refresh")
@RequiredArgsConstructor
public class RefreshApiController {

    private final RefreshService refreshService;

    /**
     * @param scope {@code seen} (default) refreshes only rated titles;
     *              {@code all} refreshes everything.
     */
    @PostMapping
    public RefreshResultDto refresh(@RequestParam(name = "scope", defaultValue = "seen") String scope) {
        return "all".equalsIgnoreCase(scope)
                ? refreshService.refreshAll()
                : refreshService.refreshSeen();
    }
}
