package tech.dobler.where2stream.accountaccess.adapter.in.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tech.dobler.where2stream.accountaccess.domain.Role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Where a successful login lands (TODO-61).
 *
 * <p>The reported symptom is that after being logged out, signing in again does not reach the
 * dashboard. The interesting part is not the login itself — that succeeds — but the redirect after
 * it, which Spring Security derives from whatever request was saved when authentication was first
 * demanded. That saved request is invisible in the browser and easy to reason about wrongly, so it
 * is pinned here instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoginRedirectTest {

    private static final String USERNAME = "login-redirect-user";
    private static final String PASSWORD = "pw-login-redirect";

    @Autowired
    private MockMvc mockMvc;

    private void ensureUser() throws Exception {
        mockMvc.perform(post("/api/admin/users").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","password":"%s","email":"%s@x.test","roles":["%s"]}"""
                        .formatted(USERNAME, PASSWORD, USERNAME, Role.USER.name())));
    }

    /** Signs in, and reports where the response sends the browser. */
    private String loginRedirect() throws Exception {
        return mockMvc.perform(post("/login")
                        .param("username", USERNAME).param("password", PASSWORD).with(csrf()))
                .andReturn().getResponse().getRedirectedUrl();
    }

    @Test
    void aLoginLandsOnTheApplication() throws Exception {
        ensureUser();

        // `/` is SpaController's redirect to /app/, and it is context-path relative -- which is why
        // this is the right target rather than a hard-coded /app/.
        assertThat(loginRedirect()).isEqualTo("/");
    }

    @Test
    void aRefusedApiCallLeavesNothingBehindThatCouldMisdirectTheNextLogin() throws Exception {
        // TODO-61 assumed the opposite: that the XHR refused when a session expires is remembered
        // as Spring Security's "saved request", so the following login returns to that API URL and
        // hands the user JSON instead of the dashboard.
        //
        // It does not happen, and this is why: answering /api/** with a bare 401 creates no session
        // at all, so there is nothing to remember. Pinned because the day someone swaps that entry
        // point for a redirect, a session appears, the saved request with it, and the failure mode
        // becomes real without any test noticing.
        final var session = mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getRequest()
                .getSession(false);

        assertThat(session).isNull();
    }

    @Test
    void aLoginLeavesAnAuthenticatedContextBehind() throws Exception {
        ensureUser();

        // Landing on the right URL is only half of it. If the login did not actually authenticate,
        // the SPA's first call would be refused and the 401 interceptor would bounce straight back
        // to the login page -- which is what TODO-61 describes.
        mockMvc.perform(post("/login")
                        .param("username", USERNAME).param("password", PASSWORD).with(csrf()))
                .andExpect(authenticated().withUsername(USERNAME));
    }

    @Test
    void aBrowserNavigationGetsTheLoginPage_anXhrGetsABare401() throws Exception {
        // Which of the two a caller gets is decided by the Accept header. That makes the SPA's
        // interceptor the only thing between an expired session and a page that silently stops
        // working -- the browser never sees a redirect for an XHR.
        mockMvc.perform(get("/app/").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/app/"))
                .andExpect(status().isUnauthorized());
    }
}
