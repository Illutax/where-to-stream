package tech.dobler.where2stream.watchlist.adapter.in.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.where2stream.watchlist.domain.NoSuchWatchlistEntryException;
import tech.dobler.where2stream.watchlist.domain.WatchlistEntryAlreadyExistsException;
import tech.dobler.where2stream.watchlist.application.WatchlistImportService;
import tech.dobler.where2stream.watchlist.application.dto.WatchlistDto;
import tech.dobler.where2stream.watchlist.application.dto.WatchlistImportResultDto;
import tech.dobler.where2stream.shared.kernel.adapter.StringToImdbIdConverter;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.shared.kernel.domain.ReleaseYear;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Import the String->ImdbId converter so @PathVariable ImdbId binds in the slice (malformed -> 400).
@WebMvcTest(WatchlistApiController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(StringToImdbIdConverter.class)
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

    @Test
    void clearSeenReturns204AndDelegates() throws Exception {
        when(watchlistImportService.resolveUserId("alice")).thenReturn(USER);

        mockMvc.perform(delete("/api/watchlist/seen").principal(alice()))
                .andExpect(status().isNoContent());

        verify(watchlistImportService).clearSeen(USER);
    }

    @Test
    void addOneReturns204AndDelegates() throws Exception {
        when(watchlistImportService.resolveUserId("alice")).thenReturn(USER);

        mockMvc.perform(post("/api/watchlist/tt0133093").principal(alice())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"The Matrix\",\"year\":1999}"))
                .andExpect(status().isNoContent());

        verify(watchlistImportService).addOne(USER, ImdbId.of("tt0133093"), "The Matrix", ReleaseYear.of(1999));
    }

    @Test
    void addOneWithoutNameOrYearIsRejectedWith400() throws Exception {
        when(watchlistImportService.resolveUserId("alice")).thenReturn(USER);

        mockMvc.perform(post("/api/watchlist/tt0133093").principal(alice())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verify(watchlistImportService, never()).addOne(any(), any(), any(), any());
    }

    @Test
    void addOneForATitleAlreadyOnTheListReturns409() throws Exception {
        when(watchlistImportService.resolveUserId("alice")).thenReturn(USER);
        doThrow(new WatchlistEntryAlreadyExistsException(ImdbId.of("tt0133093")))
                .when(watchlistImportService).addOne(USER, ImdbId.of("tt0133093"), "The Matrix", ReleaseYear.of(1999));

        mockMvc.perform(post("/api/watchlist/tt0133093").principal(alice())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"The Matrix\",\"year\":1999}"))
                .andExpect(status().isConflict());
    }

    @Test
    void markSeenReturns204AndDelegates() throws Exception {
        when(watchlistImportService.resolveUserId("alice")).thenReturn(USER);

        mockMvc.perform(put("/api/watchlist/tt0482571/seen").principal(alice())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"seen\":true}"))
                .andExpect(status().isNoContent());

        verify(watchlistImportService).markSeen(USER, ImdbId.of("tt0482571"), true);
    }

    @Test
    void markSeenWithoutTheFlagIsRejectedWith400() throws Exception {
        when(watchlistImportService.resolveUserId("alice")).thenReturn(USER);

        mockMvc.perform(put("/api/watchlist/tt0482571/seen").principal(alice())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verify(watchlistImportService, never()).markSeen(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void markSeenWithAMalformedImdbIdReturns400() throws Exception {
        mockMvc.perform(put("/api/watchlist/not-an-id/seen").principal(alice())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"seen\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void markSeenForATitleNotOnTheListReturns404() throws Exception {
        when(watchlistImportService.resolveUserId("alice")).thenReturn(USER);
        doThrow(new NoSuchWatchlistEntryException(ImdbId.of("tt9999999")))
                .when(watchlistImportService).markSeen(USER, ImdbId.of("tt9999999"), true);

        mockMvc.perform(put("/api/watchlist/tt9999999/seen").principal(alice())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"seen\":true}"))
                .andExpect(status().isNotFound());
    }
}
