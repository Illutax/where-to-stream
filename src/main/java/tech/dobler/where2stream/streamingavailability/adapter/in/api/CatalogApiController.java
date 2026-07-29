package tech.dobler.where2stream.streamingavailability.adapter.in.api;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.dobler.where2stream.streamingavailability.application.CatalogOverviewService;
import tech.dobler.where2stream.accountaccess.port.in.CurrentUserPort;
import tech.dobler.where2stream.streamingavailability.application.dto.OverviewEntryDto;

import java.util.List;

/** JSON catalogue overview for the current user — the data behind the Thymeleaf {@code index} page. */
@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogApiController {

    private final CatalogOverviewService catalogOverviewService;
    private final CurrentUserPort currentUserPort;

    @GetMapping
    public List<OverviewEntryDto> catalog(Authentication authentication) {
        return catalogOverviewService.overview(currentUserPort.resolveId(authentication.getName()));
    }
}
