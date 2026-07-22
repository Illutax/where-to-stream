package tech.dobler.werstreamt.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.werstreamt.application.CacheManagementService;
import tech.dobler.werstreamt.application.dto.InvalidateResultDto;
import tech.dobler.werstreamt.application.dto.ManagePageDto;
import tech.dobler.werstreamt.application.dto.ManageRowDto;
import tech.dobler.werstreamt.application.dto.ScrapeResultDto;
import tech.dobler.werstreamt.configurations.ThymeleafConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ManageController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ThymeleafConfig.class)
class ManageControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private CacheManagementService cacheManagementService;
    @MockitoBean
    private CommonAttributeService commonAttributeService;

    @Test
    void manageRendersTheRows() throws Exception {
        when(cacheManagementService.managePage()).thenReturn(new ManagePageDto(
                List.of(new ManageRowDto("tt1", "Alpha", true, true),
                        new ManageRowDto("tt2", "Beta", false, false)), 1));

        final var html = mockMvc.perform(get("/manage"))
                .andExpect(status().isOk())
                .andExpect(view().name("manage"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Alpha").contains("tt1").contains("needs scrape").contains("cached");
    }

    @Test
    void invalidateRedirectsBackToManage() throws Exception {
        when(cacheManagementService.invalidate(anyList())).thenReturn(new InvalidateResultDto(2));

        mockMvc.perform(post("/invalidate").param("imdbIds", "tt1", "tt2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/manage?invalidated=2"));
    }

    @Test
    void scrapeInvalidatedRedirectsBackToManage() throws Exception {
        when(cacheManagementService.scrapeUncached()).thenReturn(new ScrapeResultDto(3));

        mockMvc.perform(post("/scrape-invalidated"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/manage?scraped=3"));
    }
}
