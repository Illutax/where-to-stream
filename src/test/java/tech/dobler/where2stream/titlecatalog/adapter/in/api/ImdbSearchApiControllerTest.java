package tech.dobler.where2stream.titlecatalog.adapter.in.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.where2stream.accountaccess.port.in.CurrentUserPort;
import tech.dobler.where2stream.titlecatalog.application.ImdbSearchService;
import tech.dobler.where2stream.titlecatalog.application.command.ImdbSearchCommand;
import tech.dobler.where2stream.titlecatalog.application.dto.ImdbSearchResultDto;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.shared.kernel.domain.ReleaseYear;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    private CurrentUserPort currentUserPort;

    private static UsernamePasswordAuthenticationToken alice() {
        return new UsernamePasswordAuthenticationToken("alice", "pw");
    }

    @Test
    void searchReturnsTheResults() throws Exception {
        when(currentUserPort.resolveId("alice")).thenReturn(USER);
        when(imdbSearchService.search(new ImdbSearchCommand(USER, "matrix"))).thenReturn(List.of(
                new ImdbSearchResultDto(ImdbId.of("tt0133093"), "The Matrix", ReleaseYear.of(1999), true)));

        mockMvc.perform(get("/api/imdb/search").param("q", "matrix").principal(alice()))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"imdbId":"tt0133093","name":"The Matrix","year":1999,"onWatchlist":true}]"""));
    }

    /**
     * Pins the whole response, not just the status: TODO-68 moved this check out of the handler body
     * and into {@link ImdbSearchCommand}'s compact constructor, and what the client sees must not
     * change.
     * Both variants throw the same {@code ValidationException} from inside the controller invocation,
     * so {@code ApiExceptionHandler} produces the identical {@code application/problem+json} body —
     * asserted here rather than assumed.
     */
    @Test
    void blankQueryIsRejectedWith400() throws Exception {
        mockMvc.perform(get("/api/imdb/search").param("q", "  ").principal(alice()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().json("""
                        {"status":400,"title":"Invalid request","detail":"A search query is required."}"""));
    }

    /** No {@code q} at all never reaches the controller — Spring rejects the missing parameter itself. */
    @Test
    void missingQueryIsRejectedWith400() throws Exception {
        mockMvc.perform(get("/api/imdb/search").principal(alice()))
                .andExpect(status().isBadRequest());
    }
}
