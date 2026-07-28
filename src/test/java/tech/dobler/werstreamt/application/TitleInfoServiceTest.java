package tech.dobler.werstreamt.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.werstreamt.domain.AgeRating;
import tech.dobler.werstreamt.domain.ImdbId;
import tech.dobler.werstreamt.services.ImdbTitleClient.ImdbTitleData;
import tech.dobler.werstreamt.services.TitleMetaService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TitleInfoServiceTest {

    private static final ImdbId ID = ImdbId.of("tt1");

    @Mock
    private TitleMetaService titleMetaService;
    @InjectMocks
    private TitleInfoService service;

    @Test
    void metaForMapsTheCachedRatingAndGermanTitle() {
        final var rating = AgeRating.fsk("12");
        when(titleMetaService.get(ID)).thenReturn(Optional.of(new ImdbTitleData("poster.jpg", rating, "Der Pate")));

        final var meta = service.metaFor(ID);

        assertThat(meta).isPresent();
        assertThat(meta.get().rating()).isEqualTo(rating);
        assertThat(meta.get().germanTitle()).isEqualTo("Der Pate");
    }

    @Test
    void metaForToleratesIndividuallyNullFields() {
        when(titleMetaService.get(ID)).thenReturn(Optional.of(new ImdbTitleData(null, null, null)));

        final var meta = service.metaFor(ID);

        assertThat(meta).isPresent();
        assertThat(meta.get().rating()).isNull();
        assertThat(meta.get().germanTitle()).isNull();
    }

    @Test
    void metaForIsEmptyOnAHardFetchFailure() {
        when(titleMetaService.get(ID)).thenReturn(Optional.empty());

        assertThat(service.metaFor(ID)).isEmpty();
    }
}
