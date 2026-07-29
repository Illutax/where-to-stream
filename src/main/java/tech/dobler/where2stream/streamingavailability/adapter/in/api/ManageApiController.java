package tech.dobler.where2stream.streamingavailability.adapter.in.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.dobler.where2stream.streamingavailability.application.CacheManagementService;
import tech.dobler.where2stream.streamingavailability.application.dto.CacheResultDto;
import tech.dobler.where2stream.streamingavailability.application.dto.InvalidateResultDto;
import tech.dobler.where2stream.streamingavailability.application.dto.ManagePageDto;
import tech.dobler.where2stream.streamingavailability.application.dto.ScrapeResultDto;
import tech.dobler.where2stream.streamingavailability.application.dto.UncachedCountDto;

/** Cache-management endpoints backing the Angular "Manage cache" page. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ManageApiController {

    private final CacheManagementService cacheManagementService;

    @GetMapping("/manage")
    public ManagePageDto managePage() {
        return cacheManagementService.managePage();
    }

    @PostMapping("/manage/invalidate")
    public InvalidateResultDto invalidate(@RequestBody InvalidateRequest request) {
        return cacheManagementService.invalidate(request == null ? null : request.imdbIds());
    }

    @PostMapping("/manage/scrape")
    public ScrapeResultDto scrape() {
        return cacheManagementService.scrapeUncached();
    }

    @PostMapping("/cache")
    public CacheResultDto cacheAll() {
        return cacheManagementService.cacheAll();
    }

    @GetMapping("/cache/uncached")
    public UncachedCountDto uncachedCount() {
        return cacheManagementService.uncachedCount();
    }
}
