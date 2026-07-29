package tech.dobler.where2stream.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.where2stream.application.CatalogOverviewService;
import tech.dobler.where2stream.accountaccess.port.in.CurrentUserPort;
import tech.dobler.where2stream.application.dto.OverviewEntryDto;
import tech.dobler.where2stream.shared.domain.ImdbId;
import tech.dobler.where2stream.shared.domain.ReleaseYear;
import tech.dobler.where2stream.watchlist.domain.WatchlistDate;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CatalogApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class CatalogApiControllerTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private CatalogOverviewService catalogOverviewService;
    @MockitoBean
    private CurrentUserPort currentUserPort;

    @Test
    void catalogReturnsJsonArray() throws Exception {
        when(currentUserPort.resolveId("alice")).thenReturn(USER);
        when(catalogOverviewService.overview(eq(USER))).thenReturn(List.of(
                new OverviewEntryDto(true, "Movie", ImdbId.of("tt1"), ReleaseYear.of(2020), WatchlistDate.of("2020-01-01"), "Netflix"),
                new OverviewEntryDto(false, "Other", ImdbId.of("tt2"), ReleaseYear.of(2021), WatchlistDate.of("2021-01-01"), null)));

        mockMvc.perform(get("/api/catalog").principal(new UsernamePasswordAuthenticationToken("alice", "pw")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Movie"))
                .andExpect(jsonPath("$[0].isRated").value(true))
                .andExpect(jsonPath("$[0].services").value("Netflix"))
                .andExpect(jsonPath("$[1].services").isEmpty());
    }
}
