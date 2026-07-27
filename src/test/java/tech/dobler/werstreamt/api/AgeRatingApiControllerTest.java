package tech.dobler.werstreamt.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.werstreamt.application.AgeRatingService;
import tech.dobler.werstreamt.configurations.StringToImdbIdConverter;
import tech.dobler.werstreamt.domain.AgeRating;
import tech.dobler.werstreamt.domain.ImdbId;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgeRatingApiController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(StringToImdbIdConverter.class)
class AgeRatingApiControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AgeRatingService ageRatingService;

    @Test
    void servesTheFskRatingAsJson() throws Exception {
        when(ageRatingService.ratingFor(ImdbId.of("tt1"))).thenReturn(Optional.of(AgeRating.fsk("16")));

        mockMvc.perform(get("/api/titles/tt1/rating"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.system").value("FSK"))
                .andExpect(jsonPath("$.label").value("16"));
    }

    @Test
    void returns404WhenTheTitleHasNoRating() throws Exception {
        when(ageRatingService.ratingFor(ImdbId.of("tt404"))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/titles/tt404/rating")).andExpect(status().isNotFound());
    }

    @Test
    void returns400ForAMalformedImdbId() throws Exception {
        mockMvc.perform(get("/api/titles/not-an-id/rating")).andExpect(status().isBadRequest());
    }
}
