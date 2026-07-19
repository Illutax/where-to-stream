package tech.dobler.werstreamt.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tech.dobler.werstreamt.application.ProviderPageService;
import tech.dobler.werstreamt.application.StreamingProvider;
import tech.dobler.werstreamt.application.dto.ProviderPageDto;

/** JSON per-provider page (amazon, disney, netflix, wow, google). */
@RestController
@RequestMapping("/api/providers")
@RequiredArgsConstructor
public class ProviderApiController {

    private final ProviderPageService providerPageService;

    @GetMapping("/{provider}")
    public ProviderPageDto provider(@PathVariable String provider) {
        return StreamingProvider.fromKey(provider)
                .map(providerPageService::pageFor)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown provider: " + provider));
    }
}
