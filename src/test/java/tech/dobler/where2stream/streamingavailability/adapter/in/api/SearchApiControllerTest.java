package tech.dobler.where2stream.streamingavailability.adapter.in.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.where2stream.streamingavailability.application.SearchService;
import tech.dobler.where2stream.shared.web.StringToImdbIdConverter;
import tech.dobler.where2stream.streamingavailability.domain.Availability;
import tech.dobler.where2stream.shared.domain.ImdbId;
import tech.dobler.where2stream.streamingavailability.domain.QueryResult;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Import the String->ImdbId converter so @RequestParam("imdbId") ImdbId binds in the slice.
@WebMvcTest(SearchApiController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(StringToImdbIdConverter.class)
class SearchApiControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private SearchService searchService;

    private static QueryResult result(String imdbId) {
        return new QueryResult(ImdbId.of(imdbId), "Netflix", true, List.<Availability>of(), null);
    }

    @Test
    void searchByImdbIdReturnsResults() throws Exception {
        when(searchService.resolveByImdbId(ImdbId.of("tt1"))).thenReturn(Optional.of(List.of(result("tt1"))));

        mockMvc.perform(get("/api/search").param("imdbId", "tt1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].imdbId").value("tt1"))
                .andExpect(jsonPath("$[0].streamingServiceName").value("Netflix"));
    }

    @Test
    void missReturns404() throws Exception {
        when(searchService.resolveByImdbId(ImdbId.of("tt404"))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/search").param("imdbId", "tt404"))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingImdbIdParamReturns400() throws Exception {
        mockMvc.perform(get("/api/search"))
                .andExpect(status().isBadRequest());
    }
}
