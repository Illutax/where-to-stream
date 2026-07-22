package tech.dobler.werstreamt.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.werstreamt.application.CacheManagementService;
import tech.dobler.werstreamt.application.dto.CacheResultDto;
import tech.dobler.werstreamt.application.dto.UncachedCountDto;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Guards the legacy plain-text response contract of the pre-cache maintenance endpoints. */
@WebMvcTest(PreCacheController.class)
@AutoConfigureMockMvc(addFilters = false)
class PreCacheControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private CacheManagementService cacheManagementService;

    @Test
    void preCacheReportsHowManyEntriesWereCached() throws Exception {
        when(cacheManagementService.cacheAll()).thenReturn(new CacheResultDto(42));

        mockMvc.perform(get("/pre-cache"))
                .andExpect(status().isOk())
                .andExpect(content().string("cached 42 imdb entries"));
    }

    @Test
    void checkPreCacheReportsTheUncachedCount() throws Exception {
        when(cacheManagementService.uncachedCount()).thenReturn(new UncachedCountDto(7));

        mockMvc.perform(get("/check-pre-cache"))
                .andExpect(status().isOk())
                .andExpect(content().string("7 uncached imdb entries"));
    }
}
