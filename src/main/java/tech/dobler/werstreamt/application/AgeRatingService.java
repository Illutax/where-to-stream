package tech.dobler.werstreamt.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tech.dobler.werstreamt.domain.AgeRating;
import tech.dobler.werstreamt.domain.ImdbId;
import tech.dobler.werstreamt.services.TitleMetaService;

import java.util.Optional;

/**
 * The age rating for a title, from the shared IMDb metadata cache ({@link TitleMetaService}) — so it
 * reuses the same one fetch per title as the poster. Application-layer facade so the presentation
 * layer stays off the services layer.
 */
@Service
@RequiredArgsConstructor
public class AgeRatingService {

    private final TitleMetaService titleMetaService;

    public Optional<AgeRating> ratingFor(ImdbId imdbId) {
        return titleMetaService.ageRating(imdbId);
    }
}
