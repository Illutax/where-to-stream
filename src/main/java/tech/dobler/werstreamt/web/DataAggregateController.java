package tech.dobler.werstreamt.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import tech.dobler.werstreamt.application.CatalogOverviewService;
import tech.dobler.werstreamt.application.CurrentUserService;
import tech.dobler.werstreamt.application.ProviderPageService;
import tech.dobler.werstreamt.application.StreamingProvider;

import java.util.UUID;

/**
 * Thymeleaf pages for the current user's catalogue overview and per-provider pages. Data shaping
 * lives in {@link CatalogOverviewService} / {@link ProviderPageService}; this controller resolves
 * the current user, maps the DTOs onto model attributes and picks the view.
 */
@Controller
@RequiredArgsConstructor
public class DataAggregateController {
    private final CatalogOverviewService catalogOverviewService;
    private final ProviderPageService providerPageService;
    private final CommonAttributeService commonAttributeService;
    private final CurrentUserService currentUserService;

    @GetMapping(path = {"", "/"})
    public String index(Model model, Authentication authentication) {
        final var userId = currentUserService.resolveId(authentication.getName());
        model.addAttribute("entries", catalogOverviewService.overview(userId));
        commonAttributeService.add(model, userId);
        return "index";
    }

    @GetMapping(path = {"amazon", "prime"})
    public String getAmazon(Model model, Authentication authentication) {
        final var userId = currentUserService.resolveId(authentication.getName());
        final var page = providerPageService.pageFor(StreamingProvider.AMAZON, userId);
        model.addAttribute("primeIncluded", page.included());
        model.addAttribute("primeOthers", page.paid());
        commonAttributeService.add(model, userId);
        return "amazon";
    }

    @GetMapping(path = "disney")
    public String getDisney(Model model, Authentication authentication) {
        return flatratePage(StreamingProvider.DISNEY, "disney", model, authentication);
    }

    @GetMapping(path = "netflix")
    public String getNetflix(Model model, Authentication authentication) {
        return flatratePage(StreamingProvider.NETFLIX, "netflix", model, authentication);
    }

    @GetMapping(path = "wow")
    public String getWow(Model model, Authentication authentication) {
        return flatratePage(StreamingProvider.WOW, "wow", model, authentication);
    }

    @GetMapping(path = "google")
    public String getGoogle(Model model, Authentication authentication) {
        final var userId = currentUserService.resolveId(authentication.getName());
        model.addAttribute("entries", providerPageService.pageFor(StreamingProvider.GOOGLE, userId).paid());
        commonAttributeService.add(model, userId);
        return "google";
    }

    /** Renders a single-service "flatrate / included" page (Disney+, Netflix, WOW, …). */
    private String flatratePage(StreamingProvider provider, String view, Model model, Authentication authentication) {
        final UUID userId = currentUserService.resolveId(authentication.getName());
        model.addAttribute("entries", providerPageService.pageFor(provider, userId).included());
        commonAttributeService.add(model, userId);
        return view;
    }
}
