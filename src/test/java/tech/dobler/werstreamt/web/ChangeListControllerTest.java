package tech.dobler.werstreamt.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.werstreamt.application.ListSelectionService;
import tech.dobler.werstreamt.application.UnknownListException;
import tech.dobler.werstreamt.application.dto.ChangeListResultDto;
import tech.dobler.werstreamt.application.dto.ListSelectionDto;
import tech.dobler.werstreamt.configurations.ThymeleafConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ChangeListController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ThymeleafConfig.class)
class ChangeListControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ListSelectionService listSelectionService;
    @MockitoBean
    private CommonAttributeService commonAttributeService;

    @Test
    void listRendersCurrentAndAvailableLists() throws Exception {
        when(listSelectionService.selection()).thenReturn(new ListSelectionDto("current.csv", List.of("current.csv", "other.csv")));

        final var html = mockMvc.perform(get("/list"))
                .andExpect(status().isOk())
                .andExpect(view().name("change-list"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("current.csv").contains("other.csv");
    }

    @Test
    void changingToAKnownListRedirectsWithSuccess() throws Exception {
        when(listSelectionService.changeTo("other.csv")).thenReturn(new ChangeListResultDto("other.csv", 12));

        mockMvc.perform(post("/list-change").param("list", "other.csv"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/list?success"));
    }

    @Test
    void changingToAnUnknownListRedirectsWithError() throws Exception {
        when(listSelectionService.changeTo("nope.csv")).thenThrow(new UnknownListException("nope.csv"));

        mockMvc.perform(post("/list-change").param("list", "nope.csv"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/list?error&unknownEntry=nope.csv"));
    }
}
