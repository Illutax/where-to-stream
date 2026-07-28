package tech.dobler.werstreamt.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.werstreamt.application.CurrentUserService;
import tech.dobler.werstreamt.application.ImdbSearchService;
import tech.dobler.werstreamt.application.dto.ImdbSearchResultDto;
import tech.dobler.werstreamt.domain.ImdbId;
import tech.dobler.werstreamt.domain.ReleaseYear;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImdbSearchApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class ImdbSearchApiControllerTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ImdbSearchService imdbSearchService;
    @MockitoBean
    private CurrentUserService currentUserService;

    private static UsernamePasswordAuthenticationToken alice() {
        return new UsernamePasswordAuthenticationToken("alice", "pw");
    }

    @Test
    void searchReturnsTheResults() throws Exception {
        when(currentUserService.resolveId("alice")).thenReturn(USER);
        when(imdbSearchService.search(USER, "matrix")).thenReturn(List.of(
                new ImdbSearchResultDto(ImdbId.of("tt0133093"), "The Matrix", ReleaseYear.of(1999), true)));

        mockMvc.perform(get("/api/imdb/search").param("q", "matrix").principal(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].imdbId").value("tt0133093"))
                .andExpect(jsonPath("$[0].name").value("The Matrix"))
                .andExpect(jsonPath("$[0].year").value(1999))
                .andExpect(jsonPath("$[0].onWatchlist").value(true));
    }

    @Test
    void blankQueryIsRejectedWith400() throws Exception {
        mockMvc.perform(get("/api/imdb/search").param("q", "  ").principal(alice()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingQueryIsRejectedWith400() throws Exception {
        mockMvc.perform(get("/api/imdb/search").principal(alice()))
                .andExpect(status().isBadRequest());
    }
}
