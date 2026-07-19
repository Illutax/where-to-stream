package tech.dobler.werstreamt.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.werstreamt.application.CacheManagementService;
import tech.dobler.werstreamt.application.dto.InvalidateResultDto;
import tech.dobler.werstreamt.application.dto.ManagePageDto;
import tech.dobler.werstreamt.application.dto.ManageRowDto;
import tech.dobler.werstreamt.application.dto.ScrapeResultDto;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ManageApiController.class)
class ManageApiControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private CacheManagementService cacheManagementService;

    @Test
    void managePageReturnsRows() throws Exception {
        when(cacheManagementService.managePage()).thenReturn(new ManagePageDto(
                List.of(new ManageRowDto("tt1", "Movie", true, false)), 0));

        mockMvc.perform(get("/api/manage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsScrapeCount").value(0))
                .andExpect(jsonPath("$.rows[0].imdbId").value("tt1"));
    }

    @Test
    void invalidateReturnsCount() throws Exception {
        when(cacheManagementService.invalidate(List.of("tt1", "tt2"))).thenReturn(new InvalidateResultDto(2));

        mockMvc.perform(post("/api/manage/invalidate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imdbIds\":[\"tt1\",\"tt2\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invalidated").value(2));
    }

    @Test
    void scrapeReturnsCount() throws Exception {
        when(cacheManagementService.scrapeUncached()).thenReturn(new ScrapeResultDto(3));

        mockMvc.perform(post("/api/manage/scrape"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scraped").value(3));
    }
}
