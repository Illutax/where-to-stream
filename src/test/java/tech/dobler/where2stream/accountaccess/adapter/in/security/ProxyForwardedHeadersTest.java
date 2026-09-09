package tech.dobler.where2stream.accountaccess.adapter.in.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The application runs behind Caddy, which terminates TLS, so it only learns the real scheme and
 * host from {@code X-Forwarded-*}. These tests pin that it actually reads them.
 *
 * <p>Worth its own test class because the failure is quiet. Nothing breaks outright when the header
 * is ignored: every redirect simply points at {@code http}, the proxy bounces it back to
 * {@code https}, and the page arrives. What it costs is a plaintext hop per redirect and a login
 * flow whose target URLs are built from a scheme the request never used — which is where it was
 * first noticed (TODO-61), as a chain of 302/307 pairs on the way to the login page.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProxyForwardedHeadersTest {

    @Autowired
    private MockMvc mockMvc;

    /** Where an unauthenticated browser navigation is sent, as the proxy would present it. */
    private String redirectBehindProxy() throws Exception {
        return mockMvc.perform(get("/app/")
                        .accept(MediaType.TEXT_HTML)
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "w2s.example"))
                .andReturn()
                .getResponse()
                .getRedirectedUrl();
    }

    @Test
    void aRedirectKeepsTheSchemeAndHostTheClientActuallyUsed() throws Exception {
        // Spring turns a relative sendRedirect into an absolute URL using what it believes the
        // request was. Believing wrongly is how "https in, http out" happens.
        assertThat(redirectBehindProxy()).startsWith("https://w2s.example/");
    }

    @Test
    void theRedirectStillPointsAtTheLoginPage() throws Exception {
        // Guards the obvious way to "fix" the above and break something else: the target has to
        // stay the login page, only its scheme and host change.
        assertThat(redirectBehindProxy()).endsWith("/login");
    }

    @Test
    void withoutTheHeadersTheRedirectStaysRelativeAndClaimsNothing() throws Exception {
        // Spring itself emits a *relative* Location; something downstream makes it absolute. In
        // production that used to be the servlet container, which knew only the plaintext hop from
        // the proxy — hence the http URLs. With the headers present it is the ForwardedHeaderFilter
        // instead, working from what the client actually used.
        //
        // Without them nothing is invented: direct access (a probe on the internal network, a
        // developer on localhost) must not be told it is on https.
        final var direct = mockMvc.perform(get("/app/").accept(MediaType.TEXT_HTML))
                .andReturn().getResponse().getRedirectedUrl();

        assertThat(direct).isEqualTo("/login");
    }

    @Test
    void theForwardedHostDoesNotLeakIntoAnAuthenticatedResponse() throws Exception {
        // The filter rewrites what the application thinks the request was; it must not turn an
        // ordinary API answer into a redirect or otherwise disturb it.
        mockMvc.perform(get("/api/me").with(user("alice"))
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "w2s.example"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));
    }
}
