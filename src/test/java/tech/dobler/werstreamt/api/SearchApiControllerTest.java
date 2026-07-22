package tech.dobler.werstreamt.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.werstreamt.application.SearchService;
import tech.dobler.werstreamt.domain.Availability;
import tech.dobler.werstreamt.domain.QueryResult;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class SearchApiControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private SearchService searchService;

    private static QueryResult result(String imdbId) {
        return new QueryResult(imdbId, "Netflix", true, List.<Availability>of(), null);
    }

    @Test
    void searchByImdbIdReturnsResults() throws Exception {
        when(searchService.resolveByImdbId("tt1")).thenReturn(Optional.of(List.of(result("tt1"))));

        mockMvc.perform(get("/api/search").param("imdbId", "tt1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].imdbId").value("tt1"))
                .andExpect(jsonPath("$[0].streamingServiceName").value("Netflix"));
    }

    @Test
    void searchByCatalogIdReturnsResults() throws Exception {
        when(searchService.resolveByCatalogId(5)).thenReturn(Optional.of(List.of(result("tt5"))));

        mockMvc.perform(get("/api/search").param("id", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].imdbId").value("tt5"));
    }

    @Test
    void missReturns404() throws Exception {
        when(searchService.resolveByImdbId("tt404")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/search").param("imdbId", "tt404"))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingBothParamsReturns400() throws Exception {
        mockMvc.perform(get("/api/search"))
                .andExpect(status().isBadRequest());
    }
}
