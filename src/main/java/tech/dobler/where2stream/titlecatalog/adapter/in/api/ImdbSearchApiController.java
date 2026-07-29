package tech.dobler.where2stream.titlecatalog.adapter.in.api;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.dobler.where2stream.accountaccess.port.in.CurrentUserPort;
import tech.dobler.where2stream.titlecatalog.application.ImdbSearchService;
import tech.dobler.where2stream.shared.api.ValidationException;
import tech.dobler.where2stream.titlecatalog.application.dto.ImdbSearchResultDto;

import java.util.List;

/** The navbar title search: free-text discovery against IMDb (not the app's own cached titles). */
@RestController
@RequestMapping("/api/imdb")
@RequiredArgsConstructor
public class ImdbSearchApiController {

    private final ImdbSearchService imdbSearchService;
    private final CurrentUserPort currentUserPort;

    @GetMapping("/search")
    public List<ImdbSearchResultDto> search(Authentication authentication, @RequestParam("q") String q) {
        if (q == null || q.isBlank()) {
            throw new ValidationException("A search query is required.");
        }
        final var userId = currentUserPort.resolveId(authentication.getName());
        return imdbSearchService.search(userId, q);
    }
}
