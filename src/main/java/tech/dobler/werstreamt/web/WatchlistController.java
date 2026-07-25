package tech.dobler.werstreamt.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tech.dobler.werstreamt.application.CurrentUserService;
import tech.dobler.werstreamt.application.InvalidImportException;
import tech.dobler.werstreamt.application.WatchlistImportService;

import java.io.IOException;
import java.util.UUID;

/** The current user's own watchlist: view status and import a CSV (any authenticated user). */
@Controller
@RequestMapping("/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistImportService watchlistImportService;
    private final CurrentUserService currentUserService;
    private final CommonAttributeService commonAttributeService;

    @GetMapping
    public String watchlist(Model model, Authentication authentication) {
        final UUID userId = currentUserService.resolveId(authentication.getName());
        model.addAttribute("status", watchlistImportService.status(userId));
        commonAttributeService.add(model, userId);
        return "watchlist";
    }

    @PostMapping("/import")
    public String importCsv(@RequestParam("file") MultipartFile file,
                            Authentication authentication,
                            RedirectAttributes attributes) {
        final UUID userId = currentUserService.resolveId(authentication.getName());
        try {
            if (file == null || file.isEmpty()) {
                throw new InvalidImportException("No file uploaded.");
            }
            try (var in = file.getInputStream()) {
                final var result = watchlistImportService.importCsv(userId, in);
                attributes.addFlashAttribute("message", "Imported: +%d ~%d -%d (%d total)"
                        .formatted(result.added(), result.updated(), result.removed(), result.total()));
            }
        } catch (InvalidImportException | IOException e) {
            attributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/watchlist";
    }

    @PostMapping("/clear")
    public String clear(Authentication authentication, RedirectAttributes attributes) {
        watchlistImportService.clear(currentUserService.resolveId(authentication.getName()));
        attributes.addFlashAttribute("message", "Watchlist cleared.");
        return "redirect:/watchlist";
    }
}
