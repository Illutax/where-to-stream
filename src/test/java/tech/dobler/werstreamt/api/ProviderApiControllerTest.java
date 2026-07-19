package tech.dobler.werstreamt.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.werstreamt.application.ProviderPageService;
import tech.dobler.werstreamt.application.StreamingProvider;
import tech.dobler.werstreamt.application.dto.FlatrateEntryDto;
import tech.dobler.werstreamt.application.dto.PaidEntryDto;
import tech.dobler.werstreamt.application.dto.ProviderPageDto;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProviderApiController.class)
class ProviderApiControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ProviderPageService providerPageService;

    @Test
    void knownProviderReturnsPage() throws Exception {
        when(providerPageService.pageFor(eq(StreamingProvider.AMAZON))).thenReturn(new ProviderPageDto(
                "amazon",
                List.of(new FlatrateEntryDto(true, "Incl", "tt1", 2020, "2020-01-01")),
                List.of(new PaidEntryDto("Paid", "tt2", "kaufen: HD: 9,99 ", "2021-01-01", false, "2021", "Deutsch"))));

        mockMvc.perform(get("/api/providers/amazon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("amazon"))
                .andExpect(jsonPath("$.included[0].name").value("Incl"))
                .andExpect(jsonPath("$.paid[0].price").value("kaufen: HD: 9,99 "));
    }

    @Test
    void unknownProviderReturns404() throws Exception {
        mockMvc.perform(get("/api/providers/hbo"))
                .andExpect(status().isNotFound());
    }
}
