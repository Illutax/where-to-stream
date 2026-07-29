package tech.dobler.where2stream.titlecatalog.adapter.in.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.where2stream.titlecatalog.application.TitleInfoService;
import tech.dobler.where2stream.titlecatalog.application.dto.MetaDto;
import tech.dobler.where2stream.shared.web.StringToImdbIdConverter;
import tech.dobler.where2stream.titlecatalog.domain.AgeRating;
import tech.dobler.where2stream.shared.domain.ImdbId;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TitleMetaApiController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(StringToImdbIdConverter.class)
class TitleMetaApiControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private TitleInfoService titleInfoService;

    @Test
    void servesTheRatingAndGermanTitle() throws Exception {
        when(titleInfoService.metaFor(ImdbId.of("tt1")))
                .thenReturn(Optional.of(new MetaDto(AgeRating.fsk("16"), "Der Pate")));

        mockMvc.perform(get("/api/titles/tt1/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating.system").value("FSK"))
                .andExpect(jsonPath("$.rating.label").value("16"))
                .andExpect(jsonPath("$.germanTitle").value("Der Pate"));
    }

    @Test
    void returns200WithNullFieldsWhenThereIsNoRatingOrGermanTitle() throws Exception {
        when(titleInfoService.metaFor(ImdbId.of("tt2"))).thenReturn(Optional.of(new MetaDto(null, null)));

        mockMvc.perform(get("/api/titles/tt2/meta")).andExpect(status().isOk());
    }

    @Test
    void returns404WhenTheTitleIsUnknown() throws Exception {
        when(titleInfoService.metaFor(ImdbId.of("tt404"))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/titles/tt404/meta")).andExpect(status().isNotFound());
    }

    @Test
    void returns400ForAMalformedImdbId() throws Exception {
        mockMvc.perform(get("/api/titles/not-an-id/meta")).andExpect(status().isBadRequest());
    }
}
