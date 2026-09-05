package tech.dobler.where2stream.accountaccess.adapter.in.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.switchuser.SwitchUserGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import tech.dobler.where2stream.accountaccess.domain.Role;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin impersonation end to end (ADR-0020). */
@SpringBootTest
@AutoConfigureMockMvc
class ImpersonationTest {

    @Autowired
    private MockMvc mockMvc;
    /**
     * Creates the account over the admin API rather than calling the service.
     * {@code UserAdminService} is guarded by method security and needs an authenticated context;
     * going through HTTP also exercises the same path an operator would take.
     */
    private String ensureUser(String username, Role role) throws Exception {
        mockMvc.perform(post("/api/admin/users").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","password":"pw-%s","email":"%s@x.test","roles":["%s"]}"""
                        .formatted(username, username, username, role.name())));
        return username;
    }

    /** The authentication a session carries while an admin is switched into another account. */
    private static UsernamePasswordAuthenticationToken switchedInto(String target) {
        final var admin = new UsernamePasswordAuthenticationToken("admin", "x",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        return new UsernamePasswordAuthenticationToken(target, "x", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SwitchUserGrantedAuthority(ImpersonationConfig.SWITCHED_AUTHORITY, admin)));
    }

    @Test
    void anAdminCanSwitchIntoAnOrdinaryAccount() throws Exception {
        final var target = ensureUser("impersonation-target", Role.USER);

        mockMvc.perform(post(ImpersonationConfig.SWITCH_URL).param("username", target)
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void whileSwitchedTheApiAnswersAsTheTargetAndNamesTheAdminBehindIt() throws Exception {
        ensureUser("impersonation-target", Role.USER);

        // This field is the whole basis for the banner: without it the admin cannot tell whose
        // session they are looking at, which is the more dangerous of the two mistakes.
        mockMvc.perform(get("/api/me").with(authentication(switchedInto("impersonation-target"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("impersonation-target"))
                .andExpect(jsonPath("$.impersonatedBy").value("admin"));
    }

    @Test
    void theWayBackIsOpenToASwitchedSessionAndClosedToEveryoneElse() throws Exception {
        // While switched, the session carries the target's roles — no ROLE_ADMIN. An exit endpoint
        // behind the admin rule would lock the admin into the other account until the session died.
        mockMvc.perform(post(ImpersonationConfig.EXIT_URL)
                        .with(authentication(switchedInto("impersonation-target"))).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(ImpersonationConfig.EXIT_URL).with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(ImpersonationConfig.EXIT_URL).with(user("someone").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anOrdinarySessionReportsNoImpersonation() throws Exception {
        // A single field on the wire, from which the client derives "is an impersonation running".
        // A second, derived boolean would be two things to keep in step for no gain.
        mockMvc.perform(get("/api/me").with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$.impersonatedBy").doesNotExist());
    }

    @Test
    void impersonatingAnAdminIsRefused() throws Exception {
        final var otherAdmin = ensureUser("impersonation-admin", Role.ADMIN);

        // The rule of ADR-0020 that nothing else enforces: an admin acting as another admin could
        // use those rights without the action being traceable to them.
        mockMvc.perform(post(ImpersonationConfig.SWITCH_URL).param("username", otherAdmin)
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void aNonAdminCannotImpersonateAnyone() throws Exception {
        final var target = ensureUser("impersonation-target", Role.USER);

        mockMvc.perform(post(ImpersonationConfig.SWITCH_URL).param("username", target)
                        .with(user("someone").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void theCheckerRefusesAnAdminTargetAndPassesAnOrdinaryOne() {
        final var checker = ImpersonationConfig.refuseAdminTargets();
        final var admin = new User("a", "x", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        final var plain = new User("u", "x", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        // Asserted on the checker directly as well as through HTTP above: this is the one rule
        // whose absence would not break anything visible until it mattered.
        assertThatExceptionOfType(ImpersonationConfig.ImpersonationNotAllowedException.class)
                .isThrownBy(() -> checker.check(admin))
                .withMessageContaining("cannot be impersonated");
        checker.check(plain);
    }

    @Test
    void theOriginalUsernameIsEmptyWithoutAnAuthentication() {
        assertThat(ImpersonationConfig.originalUsername(null)).isEmpty();
    }
}
