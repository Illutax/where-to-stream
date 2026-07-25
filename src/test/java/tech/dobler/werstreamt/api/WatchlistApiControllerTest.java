package tech.dobler.werstreamt.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.werstreamt.application.WatchlistImportService;
import tech.dobler.werstreamt.application.dto.WatchlistDto;
import tech.dobler.werstreamt.application.dto.WatchlistImportResultDto;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WatchlistApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class WatchlistApiControllerTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private WatchlistImportService watchlistImportService;

    private static UsernamePasswordAuthenticationToken alice() {
        return new UsernamePasswordAuthenticationToken("alice", "pw");
    }

    @Test
    void statusReturnsCountAndLastImport() throws Exception {
        when(watchlistImportService.resolveUserId("alice")).thenReturn(USER);
        when(watchlistImportService.status(USER)).thenReturn(new WatchlistDto(7, Instant.parse("2026-01-01T00:00:00Z")));

        mockMvc.perform(get("/api/watchlist").principal(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(7));
    }

    @Test
    void importReturnsTheSyncResult() throws Exception {
        when(watchlistImportService.resolveUserId("alice")).thenReturn(USER);
        when(watchlistImportService.importCsv(eq(USER), any(InputStream.class)))
                .thenReturn(new WatchlistImportResultDto(2, 1, 3, 10));

        final var file = new MockMultipartFile("file", "list.csv", "text/csv",
                "some,csv".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/watchlist/import").file(file).principal(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(2))
                .andExpect(jsonPath("$.removed").value(3))
                .andExpect(jsonPath("$.total").value(10));
    }

    @Test
    void emptyFileUploadIsRejectedWith400() throws Exception {
        when(watchlistImportService.resolveUserId("alice")).thenReturn(USER);
        final var empty = new MockMultipartFile("file", "list.csv", "text/csv", new byte[0]);

        mockMvc.perform(multipart("/api/watchlist/import").file(empty).principal(alice()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clearReturns204AndDelegates() throws Exception {
        when(watchlistImportService.resolveUserId("alice")).thenReturn(USER);

        mockMvc.perform(delete("/api/watchlist").principal(alice()))
                .andExpect(status().isNoContent());

        verify(watchlistImportService).clear(USER);
    }
}
