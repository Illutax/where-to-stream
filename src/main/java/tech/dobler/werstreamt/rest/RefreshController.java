package tech.dobler.werstreamt.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.dobler.werstreamt.application.RefreshService;

import static org.springframework.http.ResponseEntity.ok;

/**
 * Legacy force-refresh endpoints. Kept for backwards compatibility; new clients should use
 * {@code POST /api/refresh?scope=…}.
 */
@RestController
@RequestMapping("/refresh")
@RequiredArgsConstructor
public class RefreshController {

    private final RefreshService refreshService;

    @GetMapping("seen")
    public ResponseEntity<String> refreshSeen() {
        return ok("Refreshed %s".formatted(refreshService.refreshSeen().refreshed()));
    }

    @GetMapping("all")
    public ResponseEntity<String> refreshAll() {
        return ok("Refreshed %s".formatted(refreshService.refreshAll().refreshed()));
    }
}
