package tech.dobler.werstreamt.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

/** Guards the legacy /query and /search JSON/404 contract. */
@WebMvcTest(QueryController.class)
class QueryControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private SearchService searchService;

    private static QueryResult result(String imdbId) {
        return new QueryResult(imdbId, "Netflix", true, List.<Availability>of(), null);
    }

    @Test
    void queryReturnsLiveResultsForAKnownId() throws Exception {
        when(searchService.liveQueryByCatalogId(3)).thenReturn(Optional.of(List.of(result("tt3"))));

        mockMvc.perform(get("/query").param("id", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].imdbId").value("tt3"));
    }

    @Test
    void queryReturns404ForUnknownId() throws Exception {
        when(searchService.liveQueryByCatalogId(9)).thenReturn(Optional.empty());

        mockMvc.perform(get("/query").param("id", "9"))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchByImdbIdReturnsResults() throws Exception {
        when(searchService.resolveByImdbId("tt1")).thenReturn(Optional.of(List.of(result("tt1"))));

        mockMvc.perform(get("/search").param("imdbId", "tt1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].imdbId").value("tt1"));
    }

    @Test
    void searchReturns404OnMiss() throws Exception {
        when(searchService.resolveByCatalogId(9)).thenReturn(Optional.empty());

        mockMvc.perform(get("/search").param("id", "9"))
                .andExpect(status().isNotFound());
    }
}
