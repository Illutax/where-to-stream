package tech.dobler.where2stream.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tech.dobler.where2stream.application.InvalidImportException;
import tech.dobler.where2stream.shared.api.ValidationException;
import tech.dobler.where2stream.application.WatchlistImportService;
import tech.dobler.where2stream.application.dto.WatchlistDto;
import tech.dobler.where2stream.application.dto.WatchlistImportResultDto;
import tech.dobler.where2stream.domain.ImdbId;
import tech.dobler.where2stream.domain.ReleaseYear;

import java.io.IOException;

/** The current user's own watchlist: status, CSV import (full sync), and clear. */
@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
public class WatchlistApiController {

    private final WatchlistImportService watchlistImportService;

    @GetMapping
    public WatchlistDto status(Authentication authentication) {
        return watchlistImportService.status(watchlistImportService.resolveUserId(authentication.getName()));
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WatchlistImportResultDto importCsv(Authentication authentication,
                                              @RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new InvalidImportException("No file uploaded.");
        }
        final var userId = watchlistImportService.resolveUserId(authentication.getName());
        try (var in = file.getInputStream()) {
            return watchlistImportService.importCsv(userId, in);
        }
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(Authentication authentication) {
        watchlistImportService.clear(watchlistImportService.resolveUserId(authentication.getName()));
    }

    /** Removes only the current user's watched (seen) titles. */
    @DeleteMapping("/seen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearSeen(Authentication authentication) {
        watchlistImportService.clearSeen(watchlistImportService.resolveUserId(authentication.getName()));
    }

    /** Adds a single title (found via search) to the current user's watchlist. */
    @PostMapping("/{imdbId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addOne(Authentication authentication, @PathVariable ImdbId imdbId,
                       @RequestBody AddWatchlistEntryRequest request) {
        if (request == null || request.name() == null || request.name().isBlank() || request.year() == null) {
            throw new ValidationException("A name and year are required.");
        }
        final var userId = watchlistImportService.resolveUserId(authentication.getName());
        watchlistImportService.addOne(userId, imdbId, request.name(), ReleaseYear.of(request.year()));
    }

    /** Toggle a single title's "seen" flag for the current user (the lightweight in-app marking). */
    @PutMapping("/{imdbId}/seen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markSeen(Authentication authentication,
                         @PathVariable ImdbId imdbId,
                         @RequestBody SeenUpdateRequest request) {
        if (request == null || request.seen() == null) {
            throw new ValidationException("A 'seen' flag is required.");
        }
        final var userId = watchlistImportService.resolveUserId(authentication.getName());
        watchlistImportService.markSeen(userId, imdbId, request.seen());
    }
}
