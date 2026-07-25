package tech.dobler.werstreamt.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.werstreamt.application.CurrentUserService;
import tech.dobler.werstreamt.application.ProviderPageService;
import tech.dobler.werstreamt.application.StreamingProvider;
import tech.dobler.werstreamt.application.dto.FlatrateEntryDto;
import tech.dobler.werstreamt.application.dto.PaidEntryDto;
import tech.dobler.werstreamt.application.dto.ProviderPageDto;
import tech.dobler.werstreamt.domain.ImdbId;
import tech.dobler.werstreamt.domain.ReleaseYear;
import tech.dobler.werstreamt.domain.WatchlistDate;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProviderApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProviderApiControllerTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ProviderPageService providerPageService;
    @MockitoBean
    private CurrentUserService currentUserService;

    private static UsernamePasswordAuthenticationToken alice() {
        return new UsernamePasswordAuthenticationToken("alice", "pw");
    }

    @Test
    void knownProviderReturnsPage() throws Exception {
        when(currentUserService.resolveId("alice")).thenReturn(USER);
        when(providerPageService.pageFor(eq(StreamingProvider.AMAZON), eq(USER))).thenReturn(new ProviderPageDto(
                "amazon",
                List.of(new FlatrateEntryDto(true, "Incl", ImdbId.of("tt1"), ReleaseYear.of(2020), WatchlistDate.of("2020-01-01"))),
                List.of(new PaidEntryDto("Paid", ImdbId.of("tt2"), "kaufen: HD: 9,99 ", WatchlistDate.of("2021-01-01"), false, "2021", "Deutsch"))));

        mockMvc.perform(get("/api/providers/amazon").principal(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("amazon"))
                .andExpect(jsonPath("$.included[0].name").value("Incl"))
                .andExpect(jsonPath("$.paid[0].price").value("kaufen: HD: 9,99 "));
    }

    @Test
    void unknownProviderReturns404() throws Exception {
        when(currentUserService.resolveId("alice")).thenReturn(USER);

        mockMvc.perform(get("/api/providers/hbo").principal(alice()))
                .andExpect(status().isNotFound());
    }
}
