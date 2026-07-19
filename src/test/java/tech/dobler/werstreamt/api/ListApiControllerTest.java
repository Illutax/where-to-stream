package tech.dobler.werstreamt.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.werstreamt.application.ListSelectionService;
import tech.dobler.werstreamt.application.UnknownListException;
import tech.dobler.werstreamt.application.dto.ChangeListResultDto;
import tech.dobler.werstreamt.application.dto.ListSelectionDto;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ListApiController.class)
class ListApiControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ListSelectionService listSelectionService;

    @Test
    void listsReturnsCurrentAndAvailable() throws Exception {
        when(listSelectionService.selection()).thenReturn(new ListSelectionDto("current", List.of("a", "current")));

        mockMvc.perform(get("/api/lists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current").value("current"))
                .andExpect(jsonPath("$.available.length()").value(2));
    }

    @Test
    void changeSelectionReturnsResult() throws Exception {
        when(listSelectionService.changeTo("target")).thenReturn(new ChangeListResultDto("target", 5));

        mockMvc.perform(put("/api/lists/selection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"target\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selected").value("target"))
                .andExpect(jsonPath("$.cached").value(5));
    }

    @Test
    void unknownListYields400ProblemDetail() throws Exception {
        when(listSelectionService.changeTo("nope")).thenThrow(new UnknownListException("nope"));

        mockMvc.perform(put("/api/lists/selection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Unknown list"))
                .andExpect(jsonPath("$.listName").value("nope"));
    }
}
