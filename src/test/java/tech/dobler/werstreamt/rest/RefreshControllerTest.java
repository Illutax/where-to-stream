package tech.dobler.werstreamt.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.werstreamt.application.RefreshService;
import tech.dobler.werstreamt.application.dto.RefreshResultDto;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Guards the legacy plain-text response contract of the /refresh maintenance endpoints. */
@WebMvcTest(RefreshController.class)
@AutoConfigureMockMvc(addFilters = false)
class RefreshControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private RefreshService refreshService;

    @Test
    void refreshSeenReportsCount() throws Exception {
        when(refreshService.refreshSeen()).thenReturn(new RefreshResultDto(5));

        mockMvc.perform(get("/refresh/seen"))
                .andExpect(status().isOk())
                .andExpect(content().string("Refreshed 5"));
    }

    @Test
    void refreshAllReportsCount() throws Exception {
        when(refreshService.refreshAll()).thenReturn(new RefreshResultDto(11));

        mockMvc.perform(get("/refresh/all"))
                .andExpect(status().isOk())
                .andExpect(content().string("Refreshed 11"));
    }
}
