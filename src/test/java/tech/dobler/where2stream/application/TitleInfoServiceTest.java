package tech.dobler.where2stream.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.where2stream.application.dto.MetaDto;
import tech.dobler.where2stream.domain.AgeRating;
import tech.dobler.where2stream.domain.ImdbId;
import tech.dobler.where2stream.services.ImdbTitleClient.ImdbTitleData;
import tech.dobler.where2stream.services.TitleMetaService;

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

        assertThat(meta).contains(new MetaDto(rating, "Der Pate"));
    }

    @Test
    void metaForToleratesIndividuallyNullFields() {
        when(titleMetaService.get(ID)).thenReturn(Optional.of(new ImdbTitleData(null, null, null)));

        final var meta = service.metaFor(ID);

        assertThat(meta).contains(new MetaDto(null, null));
    }

    @Test
    void metaForIsEmptyOnAHardFetchFailure() {
        when(titleMetaService.get(ID)).thenReturn(Optional.empty());

        assertThat(service.metaFor(ID)).isEmpty();
    }
}
