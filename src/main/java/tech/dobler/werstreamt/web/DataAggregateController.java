package tech.dobler.werstreamt.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import tech.dobler.werstreamt.application.CatalogOverviewService;
import tech.dobler.werstreamt.application.ProviderPageService;
import tech.dobler.werstreamt.application.StreamingProvider;

/**
 * Thymeleaf pages for the catalogue overview and the per-provider pages. All data shaping lives
 * in {@link CatalogOverviewService} / {@link ProviderPageService}; this controller only maps
 * the DTOs onto the model attributes the templates expect and picks the view.
 */
@Controller
@RequiredArgsConstructor
public class DataAggregateController {
    private final CatalogOverviewService catalogOverviewService;
    private final ProviderPageService providerPageService;
    private final CommonAttributeService commonAttributeService;

    @GetMapping(path = {"", "/"})
    public String index(Model model) {
        model.addAttribute("entries", catalogOverviewService.overview());
        commonAttributeService.add(model);
        return "index";
    }

    @GetMapping(path = {"amazon", "prime"})
    public String getAmazon(Model model) {
        final var page = providerPageService.pageFor(StreamingProvider.AMAZON);
        model.addAttribute("primeIncluded", page.included());
        model.addAttribute("primeOthers", page.paid());
        commonAttributeService.add(model);
        return "amazon";
    }

    @GetMapping(path = "disney")
    public String getDisney(Model model) {
        return flatratePage(StreamingProvider.DISNEY, "disney", model);
    }

    @GetMapping(path = "netflix")
    public String getNetflix(Model model) {
        return flatratePage(StreamingProvider.NETFLIX, "netflix", model);
    }

    @GetMapping(path = "wow")
    public String getWow(Model model) {
        return flatratePage(StreamingProvider.WOW, "wow", model);
    }

    @GetMapping(path = "google")
    public String getGoogle(Model model) {
        model.addAttribute("entries", providerPageService.pageFor(StreamingProvider.GOOGLE).paid());
        commonAttributeService.add(model);
        return "google";
    }

    /** Renders a single-service "flatrate / included" page (Disney+, Netflix, WOW, …). */
    private String flatratePage(StreamingProvider provider, String view, Model model) {
        model.addAttribute("entries", providerPageService.pageFor(provider).included());
        commonAttributeService.add(model);
        return view;
    }
}
