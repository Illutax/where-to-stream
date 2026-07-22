package tech.dobler.werstreamt.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.werstreamt.application.UserAdminService;
import tech.dobler.werstreamt.application.UserManagementException;
import tech.dobler.werstreamt.application.dto.UserDto;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminUserApiControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UserAdminService userAdminService;

    private static UserDto dto(String id, String username) {
        return new UserDto(id, username, username + "@x", true, List.of("USER"), "LOCAL");
    }

    @Test
    void listReturnsUsers() throws Exception {
        when(userAdminService.list()).thenReturn(List.of(dto("1", "admin"), dto("2", "bob")));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].username").value("admin"));
    }

    @Test
    void createReturns201WithTheCreatedUser() throws Exception {
        when(userAdminService.create(any())).thenReturn(dto("9", "bob"));

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"password\":\"pw\",\"roles\":[\"USER\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("bob"));
    }

    @Test
    void updateReturnsTheUpdatedUser() throws Exception {
        final var id = UUID.randomUUID();
        when(userAdminService.update(eq(id), any())).thenReturn(dto(id.toString(), "bob"));

        mockMvc.perform(put("/api/admin/users/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bob@x\",\"roles\":[\"USER\"],\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void deleteReturns204() throws Exception {
        final var id = UUID.randomUUID();

        mockMvc.perform(delete("/api/admin/users/" + id))
                .andExpect(status().isNoContent());

        verify(userAdminService).delete(id);
    }

    @Test
    void resetPasswordReturns204() throws Exception {
        final var id = UUID.randomUUID();

        mockMvc.perform(post("/api/admin/users/" + id + "/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"new\"}"))
                .andExpect(status().isNoContent());

        verify(userAdminService).resetPassword(id, "new");
    }

    @Test
    void lastAdminConflictBecomesA409ProblemDetail() throws Exception {
        final var id = UUID.randomUUID();
        org.mockito.Mockito.doThrow(UserManagementException.lastAdmin()).when(userAdminService).delete(id);

        mockMvc.perform(delete("/api/admin/users/" + id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(HttpStatus.CONFLICT.value()));
    }
}
