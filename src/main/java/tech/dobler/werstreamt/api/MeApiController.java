package tech.dobler.werstreamt.api;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.dobler.werstreamt.application.dto.MeDto;

import java.util.List;

/** Exposes the current principal (username + roles) for the Angular client. */
@RestController
@RequestMapping("/api/me")
public class MeApiController {

    @GetMapping
    public MeDto me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return new MeDto(false, null, List.of(), false);
        }
        final List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .sorted()
                .toList();
        return new MeDto(true, authentication.getName(), roles, roles.contains("ADMIN"));
    }
}
