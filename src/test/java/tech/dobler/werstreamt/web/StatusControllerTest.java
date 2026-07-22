package tech.dobler.werstreamt.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.werstreamt.application.StatusService;
import tech.dobler.werstreamt.application.dto.StatusDto;
import tech.dobler.werstreamt.configurations.ThymeleafConfig;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(StatusController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ThymeleafConfig.class)
class StatusControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private StatusService statusService;

    @Test
    void statusRendersVersionAndServerStart() throws Exception {
        when(statusService.status()).thenReturn(new StatusDto("1.2.3", Instant.parse("2026-01-01T00:00:00Z")));

        final var html = mockMvc.perform(get("/public/status"))
                .andExpect(status().isOk())
                .andExpect(view().name("statusView"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("1.2.3").contains("2026-01-01");
    }
}
