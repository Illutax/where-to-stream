package tech.dobler.where2stream.streamingavailability.adapter.in.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.where2stream.streamingavailability.application.CacheManagementService;
import tech.dobler.where2stream.streamingavailability.application.command.InvalidateCommand;
import tech.dobler.where2stream.streamingavailability.application.dto.InvalidateResultDto;
import tech.dobler.where2stream.streamingavailability.application.dto.ManagePageDto;
import tech.dobler.where2stream.streamingavailability.application.dto.ManageRowDto;
import tech.dobler.where2stream.streamingavailability.application.dto.ScrapeResultDto;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ManageApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class ManageApiControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private CacheManagementService cacheManagementService;

    @Test
    void managePageReturnsRows() throws Exception {
        when(cacheManagementService.managePage()).thenReturn(new ManagePageDto(
                List.of(new ManageRowDto(ImdbId.of("tt1"), "Movie", true, false)), 0));

        mockMvc.perform(get("/api/manage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsScrapeCount").value(0))
                .andExpect(jsonPath("$.rows[0].imdbId").value("tt1"));
    }

    @Test
    void invalidateReturnsCount() throws Exception {
        when(cacheManagementService.invalidate(new InvalidateCommand(List.of(ImdbId.of("tt1"), ImdbId.of("tt2")))))
                .thenReturn(new InvalidateResultDto(2));

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
