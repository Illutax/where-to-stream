package tech.dobler.werstreamt.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.dobler.werstreamt.application.CatalogOverviewService;
import tech.dobler.werstreamt.application.dto.OverviewEntryDto;

import java.util.List;

/** JSON catalogue overview — the data behind the Thymeleaf {@code index} page. */
@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogApiController {

    private final CatalogOverviewService catalogOverviewService;

    @GetMapping
    public List<OverviewEntryDto> catalog() {
        return catalogOverviewService.overview();
    }
}
