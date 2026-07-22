package tech.dobler.werstreamt.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.werstreamt.application.RefreshService;
import tech.dobler.werstreamt.application.dto.RefreshResultDto;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RefreshApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class RefreshApiControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private RefreshService refreshService;

    @Test
    void defaultScopeRefreshesSeen() throws Exception {
        when(refreshService.refreshSeen()).thenReturn(new RefreshResultDto(4));

        mockMvc.perform(post("/api/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshed").value(4));

        verify(refreshService).refreshSeen();
        verifyNoMoreInteractions(refreshService);
    }

    @Test
    void scopeAllRefreshesEverything() throws Exception {
        when(refreshService.refreshAll()).thenReturn(new RefreshResultDto(9));

        mockMvc.perform(post("/api/refresh").param("scope", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshed").value(9));

        verify(refreshService).refreshAll();
        verifyNoMoreInteractions(refreshService);
    }
}
