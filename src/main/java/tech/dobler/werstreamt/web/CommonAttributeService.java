package tech.dobler.werstreamt.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import tech.dobler.werstreamt.application.WatchlistImportService;

import java.util.UUID;

/**
 * Adds attributes shared by all Thymeleaf pages (the current user's watchlist size, shown in the
 * navbar). Presentation-layer helper sourcing its data from the application layer.
 */
@Component
@RequiredArgsConstructor
public class CommonAttributeService {
    private final WatchlistImportService watchlistImportService;

    public void add(Model model, UUID userId) {
        model.addAttribute("watchlistCount", watchlistImportService.count(userId));
    }
}
