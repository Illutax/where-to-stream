package tech.dobler.where2stream.accountaccess.adapter.in.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * Pins the Content-Security-Policy.
 *
 * <p>A CSP is the kind of thing that is easy to add and easy to lose: a later
 * {@code .headers(...)} customiser, a Spring Security upgrade, or a well-meant "simplification"
 * can drop it without any test going red. Nothing else in this suite would notice, because a
 * missing CSP breaks nothing — it just stops protecting.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ContentSecurityPolicyTest {

    @Autowired
    private MockMvc mockMvc;

    private String policy() throws Exception {
        return mockMvc.perform(get("/api/me").with(user("alice")))
                .andReturn()
                .getResponse()
                .getHeader("Content-Security-Policy");
    }

    @Test
    void everyResponseCarriesAPolicy() throws Exception {
        mockMvc.perform(get("/api/me").with(user("alice")))
                .andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    void theLoginPageCarriesItTooEvenWithoutAuthentication() throws Exception {
        // The login page is the one server-rendered page and is reachable unauthenticated — the
        // policy has to be there before anyone is logged in, not only afterwards.
        mockMvc.perform(get("/login"))
                .andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    void nothingMayLoadFromAnotherOrigin() throws Exception {
        assertThat(policy())
                .contains("default-src 'self'")
                .contains("connect-src 'self'")
                .contains("img-src 'self'")
                .contains("font-src 'self'");
    }

    @Test
    void scriptsAreRestrictedToThisOriginWithNoInlineEscapeHatch() throws Exception {
        // The directive that actually matters. `unsafe-inline` here would make the whole policy
        // close to decorative, and it is only avoidable because Angular's critical-CSS inlining is
        // switched off in angular.json — that feature emits an inline onload handler.
        assertThat(policy()).contains("script-src 'self'");
        assertThat(policy()).doesNotContain("script-src 'self' 'unsafe-inline'");
        assertThat(policy()).doesNotContain("unsafe-eval");
    }

    @Test
    void stylesAllowInlineBecauseAngularInjectsThemAtRuntime() throws Exception {
        // Documented rather than aspired to: Angular adds component styles as <style> elements at
        // runtime. Removing this would need a per-request nonce and a templated index.html.
        assertThat(policy()).contains("style-src 'self' 'unsafe-inline'");
    }

    @Test
    void theUsualFootgunsAreClosed() throws Exception {
        assertThat(policy())
                .contains("object-src 'none'")
                .contains("base-uri 'self'")
                .contains("form-action 'self'")
                .contains("frame-ancestors 'none'");
    }

    @Test
    void noEbayOriginIsAllowedBecauseTheLookupRunsOnTheServer() throws Exception {
        // If an eBay origin ever appears here, someone moved the integration into the browser —
        // which the plan rules out in section 3, and which would put credentials there too.
        assertThat(policy()).doesNotContain("ebay");
    }
}
