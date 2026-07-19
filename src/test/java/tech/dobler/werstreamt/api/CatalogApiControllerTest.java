package tech.dobler.werstreamt.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.werstreamt.application.CatalogOverviewService;
import tech.dobler.werstreamt.application.dto.OverviewEntryDto;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CatalogApiController.class)
class CatalogApiControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private CatalogOverviewService catalogOverviewService;

    @Test
    void catalogReturnsJsonArray() throws Exception {
        when(catalogOverviewService.overview()).thenReturn(List.of(
                new OverviewEntryDto(true, "Movie", "tt1", 2020, "2020-01-01", "Netflix"),
                new OverviewEntryDto(false, "Other", "tt2", 2021, "2021-01-01", null)));

        mockMvc.perform(get("/api/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Movie"))
                .andExpect(jsonPath("$[0].isRated").value(true))
                .andExpect(jsonPath("$[0].services").value("Netflix"))
                .andExpect(jsonPath("$[1].services").isEmpty());
    }
}
