package tech.dobler.werstreamt.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real {@link SecurityConfig} filter chain: authentication requirement, the ADMIN
 * gate on mutations/administration, the JSON-401-vs-redirect split, and CSRF. Uses the
 * {@code user()}/{@code csrf()} request post-processors so the mock auth reliably reaches MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityRulesTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousApiRequestGets401() throws Exception {
        mockMvc.perform(get("/api/status")).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousPageRequestRedirectsToLogin() throws Exception {
        // A browser (Accept: text/html) is redirected to the login page; non-HTML clients get 401.
        mockMvc.perform(get("/").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void authenticatedUserCanReadStatus() throws Exception {
        mockMvc.perform(get("/api/status").with(user("u").roles("USER")))
                .andExpect(status().isOk());
    }

    @Test
    void userIsForbiddenFromUserAdministration() throws Exception {
        mockMvc.perform(get("/api/admin/users").with(user("u").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void userIsForbiddenFromMutations() throws Exception {
        mockMvc.perform(post("/api/refresh").with(user("u").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanMutateWithCsrf() throws Exception {
        mockMvc.perform(post("/api/manage/invalidate").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"imdbIds\":[]}"))
                .andExpect(status().isOk());
    }

    @Test
    void mutationWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/api/manage/invalidate").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"imdbIds\":[]}"))
                .andExpect(status().isForbidden());
    }
}
