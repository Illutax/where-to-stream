package tech.dobler.werstreamt.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Full context so the security filter chain wraps the request (the Authentication method arg
// resolves from request.getUserPrincipal(), which the @WebMvcTest slice does not wire up).
@SpringBootTest
@AutoConfigureMockMvc
class MeApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reportsTheCurrentUserWithRolesAndAdminFlag() throws Exception {
        mockMvc.perform(get("/api/me").with(user("alice").roles("ADMIN", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.admin").value(true))
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.roles[1]").value("USER"));
    }

    @Test
    void reportsANonAdminUser() throws Exception {
        mockMvc.perform(get("/api/me").with(user("bob").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("bob"))
                .andExpect(jsonPath("$.admin").value(false));
    }
}
