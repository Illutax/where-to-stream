package tech.dobler.werstreamt.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.werstreamt.application.UserAdminService;
import tech.dobler.werstreamt.application.UserManagementException;
import tech.dobler.werstreamt.application.dto.UserDto;
import tech.dobler.werstreamt.configurations.ThymeleafConfig;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ThymeleafConfig.class)
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UserAdminService userAdminService;
    @MockitoBean
    private CommonAttributeService commonAttributeService;

    @Test
    void listRendersTheUsers() throws Exception {
        when(userAdminService.list()).thenReturn(List.of(
                new UserDto("1", "admin", "a@x", true, List.of("ADMIN", "USER"), "LOCAL")));

        final var html = mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("admin").contains("User administration");
    }

    @Test
    void createRedirectsBackWithAMessage() throws Exception {
        when(userAdminService.create(any())).thenReturn(
                new UserDto("2", "bob", null, true, List.of("USER"), "LOCAL"));

        mockMvc.perform(post("/admin/users")
                        .param("username", "bob").param("password", "pw").param("roles", "USER"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attributeExists("message"));
    }

    @Test
    void deleteRedirects() throws Exception {
        final var id = UUID.randomUUID();

        mockMvc.perform(post("/admin/users/" + id + "/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        verify(userAdminService).delete(id);
    }

    @Test
    void lastAdminErrorBecomesAFlashMessage() throws Exception {
        final var id = UUID.randomUUID();
        doThrow(UserManagementException.lastAdmin()).when(userAdminService).delete(id);

        mockMvc.perform(post("/admin/users/" + id + "/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
    }
}
