package tech.dobler.where2stream.titlecatalog.adapter.in.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.where2stream.titlecatalog.application.PosterService;
import tech.dobler.where2stream.titlecatalog.application.PosterService.Poster;
import tech.dobler.where2stream.shared.web.StringToImdbIdConverter;
import tech.dobler.where2stream.shared.domain.ImdbId;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Import the String->ImdbId converter so @PathVariable ImdbId binds in the slice (malformed -> 400).
@WebMvcTest(PosterApiController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(StringToImdbIdConverter.class)
class PosterApiControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private PosterService posterService;

    @Test
    void servesTheThumbnailBytesWithCachingHeaders() throws Exception {
        when(posterService.thumb(ImdbId.of("tt1")))
                .thenReturn(Optional.of(new Poster(new byte[]{1, 2, 3}, "image/jpeg")));

        final var response = mockMvc.perform(get("/api/titles/tt1/poster"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(new byte[]{1, 2, 3}))
                .andReturn().getResponse();

        assertThat(response.getHeader("Cache-Control")).contains("max-age=", "public", "immutable");
        assertThat(response.getHeader("ETag")).isNotBlank();
    }

    @Test
    void servesTheFullImageBytes() throws Exception {
        when(posterService.full(ImdbId.of("tt1")))
                .thenReturn(Optional.of(new Poster(new byte[]{9, 9}, "image/jpeg")));

        mockMvc.perform(get("/api/titles/tt1/poster/full"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[]{9, 9}));
    }

    @Test
    void returns404WhenThereIsNoPoster() throws Exception {
        when(posterService.thumb(ImdbId.of("tt404"))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/titles/tt404/poster")).andExpect(status().isNotFound());
    }

    @Test
    void returns400ForAMalformedImdbId() throws Exception {
        mockMvc.perform(get("/api/titles/not-an-id/poster")).andExpect(status().isBadRequest());
    }
}
