package tech.dobler.werstreamt.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.werstreamt.application.CurrentUserService;
import tech.dobler.werstreamt.application.InvalidImportException;
import tech.dobler.werstreamt.application.WatchlistImportService;
import tech.dobler.werstreamt.application.dto.WatchlistDto;
import tech.dobler.werstreamt.application.dto.WatchlistImportResultDto;
import tech.dobler.werstreamt.configurations.ThymeleafConfig;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(WatchlistController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ThymeleafConfig.class)
class WatchlistControllerTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private WatchlistImportService watchlistImportService;
    @MockitoBean
    private CurrentUserService currentUserService;
    @MockitoBean
    private CommonAttributeService commonAttributeService;

    private final UsernamePasswordAuthenticationToken alice = new UsernamePasswordAuthenticationToken("alice", "pw");

    @BeforeEach
    void resolveCurrentUser() {
        when(currentUserService.resolveId("alice")).thenReturn(USER);
    }

    @Test
    void watchlistPageRendersTheStatus() throws Exception {
        when(watchlistImportService.status(USER)).thenReturn(new WatchlistDto(12, null));

        final var html = mockMvc.perform(get("/watchlist").principal(alice))
                .andExpect(status().isOk())
                .andExpect(view().name("watchlist"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("12").contains("My Watchlist");
    }

    @Test
    void importRedirectsBackWithASummaryMessage() throws Exception {
        when(watchlistImportService.importCsv(eq(USER), any(InputStream.class)))
                .thenReturn(new WatchlistImportResultDto(2, 1, 0, 3));
        final var file = new MockMultipartFile("file", "list.csv", "text/csv",
                "csv".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/watchlist/import").file(file).principal(alice))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/watchlist"))
                .andExpect(flash().attributeExists("message"));
    }

    @Test
    void emptyImportRedirectsBackWithAnError() throws Exception {
        final var empty = new MockMultipartFile("file", "list.csv", "text/csv", new byte[0]);

        mockMvc.perform(multipart("/watchlist/import").file(empty).principal(alice))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/watchlist"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void importErrorFromTheServiceBecomesAFlashError() throws Exception {
        when(watchlistImportService.importCsv(eq(USER), any(InputStream.class)))
                .thenThrow(new InvalidImportException("broken file"));
        final var file = new MockMultipartFile("file", "list.csv", "text/csv",
                "csv".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/watchlist/import").file(file).principal(alice))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "broken file"));
    }

    @Test
    void clearRedirectsBackWithAMessage() throws Exception {
        mockMvc.perform(post("/watchlist/clear").principal(alice))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/watchlist"))
                .andExpect(flash().attributeExists("message"));

        verify(watchlistImportService).clear(USER);
    }
}
