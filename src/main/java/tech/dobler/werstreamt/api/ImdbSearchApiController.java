package tech.dobler.werstreamt.api;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.dobler.werstreamt.application.CurrentUserService;
import tech.dobler.werstreamt.application.ImdbSearchService;
import tech.dobler.werstreamt.application.ValidationException;
import tech.dobler.werstreamt.application.dto.ImdbSearchResultDto;

import java.util.List;

/** The navbar title search: free-text discovery against IMDb (not the app's own cached titles). */
@RestController
@RequestMapping("/api/imdb")
@RequiredArgsConstructor
public class ImdbSearchApiController {

    private final ImdbSearchService imdbSearchService;
    private final CurrentUserService currentUserService;

    @GetMapping("/search")
    public List<ImdbSearchResultDto> search(Authentication authentication, @RequestParam("q") String q) {
        if (q == null || q.isBlank()) {
            throw new ValidationException("A search query is required.");
        }
        final var userId = currentUserService.resolveId(authentication.getName());
        return imdbSearchService.search(userId, q);
    }
}
