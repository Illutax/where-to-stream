package tech.dobler.where2stream.shared.platform.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.where2stream.shared.platform.web.StatusService;
import tech.dobler.where2stream.shared.platform.web.StatusDto;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatusController.class)
@AutoConfigureMockMvc(addFilters = false)
class StatusControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private StatusService statusService;

    @Test
    void statusReturnsVersionAndServerStartAsJson() throws Exception {
        when(statusService.status()).thenReturn(new StatusDto("1.2.3", Instant.parse("2026-01-01T00:00:00Z")));

        mockMvc.perform(get("/public/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("1.2.3"))
                .andExpect(jsonPath("$.serverStart").value("2026-01-01T00:00:00Z"));
    }
}
