package tech.dobler.werstreamt.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.werstreamt.application.CatalogOverviewService;
import tech.dobler.werstreamt.application.ProviderPageService;
import tech.dobler.werstreamt.application.StreamingProvider;
import tech.dobler.werstreamt.application.dto.FlatrateEntryDto;
import tech.dobler.werstreamt.application.dto.OverviewEntryDto;
import tech.dobler.werstreamt.application.dto.PaidEntryDto;
import tech.dobler.werstreamt.application.dto.ProviderPageDto;
import tech.dobler.werstreamt.configurations.ThymeleafConfig;
import tech.dobler.werstreamt.services.CommonAttributeService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Renders the real Thymeleaf templates with mocked services. Beyond status/view-name, this
 * guards the coupling between the DTO field names and the {@code th:*} expressions in the
 * templates — a rename on one side that the other misses fails here instead of at runtime.
 * {@link ThymeleafConfig} is imported for the layout dialect used by {@code layout:decorate}.
 */
@WebMvcTest(DataAggregateController.class)
@Import(ThymeleafConfig.class)
class DataAggregateControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private CatalogOverviewService catalogOverviewService;
    @MockitoBean
    private ProviderPageService providerPageService;
    @MockitoBean
    private CommonAttributeService commonAttributeService;

    @Test
    void indexRendersTheOverviewEntries() throws Exception {
        when(catalogOverviewService.overview()).thenReturn(List.of(
                new OverviewEntryDto(true, "Cast Away", "tt0162222", 2000, "2020-01-01", "Netflix"),
                new OverviewEntryDto(false, "Unavailable", "tt0000001", 2021, "2021-01-01", null)));

        final var html = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Cast Away").contains("tt0162222").contains("Netflix");
        // null services renders the N/A fallback
        assertThat(html).contains("N/A");
    }

    @Test
    void amazonRendersIncludedAndPaidLists() throws Exception {
        when(providerPageService.pageFor(StreamingProvider.AMAZON)).thenReturn(new ProviderPageDto(
                "amazon",
                List.of(new FlatrateEntryDto(true, "Included Film", "tt1", 2019, "2019-01-01")),
                List.of(new PaidEntryDto("Paid Film", "tt2", "kaufen: HD: 9,99 ", "2020-01-01", false, "2020", "Deutsch"))));

        final var html = mockMvc.perform(get("/amazon"))
                .andExpect(status().isOk())
                .andExpect(view().name("amazon"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Included Film").contains("Paid Film")
                .contains("kaufen: HD: 9,99").contains("Deutsch");
    }

    @Test
    void googleRendersPaidTitles() throws Exception {
        when(providerPageService.pageFor(StreamingProvider.GOOGLE)).thenReturn(new ProviderPageDto(
                "google",
                List.of(),
                List.of(new PaidEntryDto("Buyable", "tt3", "kaufen: SD: 4,99 ", "2020-01-01", false, "Not yet released", null))));

        final var html = mockMvc.perform(get("/google"))
                .andExpect(status().isOk())
                .andExpect(view().name("google"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Buyable").contains("Not yet released");
    }

    @Test
    void netflixRendersTheFlatrateList() throws Exception {
        when(providerPageService.pageFor(StreamingProvider.NETFLIX)).thenReturn(new ProviderPageDto(
                "netflix",
                List.of(new FlatrateEntryDto(false, "Nolan Film", "tt4", 2020, "2020-02-02")),
                List.of()));

        mockMvc.perform(get("/netflix"))
                .andExpect(status().isOk())
                .andExpect(view().name("netflix"));
    }
}
