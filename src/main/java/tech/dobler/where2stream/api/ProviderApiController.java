package tech.dobler.where2stream.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tech.dobler.where2stream.application.CurrentUserService;
import tech.dobler.where2stream.application.ProviderPageService;
import tech.dobler.where2stream.application.StreamingProvider;
import tech.dobler.where2stream.application.dto.ProviderPageDto;

/** JSON per-provider page (amazon, disney, netflix, wow, youtube) for the current user. */
@RestController
@RequestMapping("/api/providers")
@RequiredArgsConstructor
public class ProviderApiController {

    private final ProviderPageService providerPageService;
    private final CurrentUserService currentUserService;

    @GetMapping("/{provider}")
    public ProviderPageDto provider(@PathVariable String provider, Authentication authentication) {
        final var userId = currentUserService.resolveId(authentication.getName());
        return StreamingProvider.fromKey(provider)
                .map(p -> providerPageService.pageFor(p, userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown provider: " + provider));
    }
}
