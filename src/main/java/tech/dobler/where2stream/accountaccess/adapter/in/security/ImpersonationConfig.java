package tech.dobler.where2stream.accountaccess.adapter.in.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.switchuser.SwitchUserFilter;
import org.springframework.security.web.authentication.switchuser.SwitchUserGrantedAuthority;

import java.util.Optional;

/**
 * Lets an ADMIN act as another user for a while (ADR-0020), through Spring Security's
 * {@link SwitchUserFilter} rather than a hand-rolled equivalent.
 *
 * <p>Entered with {@code POST /api/admin/impersonate?username=…}, which is ADMIN-only through the
 * existing {@code /api/admin/**} rule. Left with {@code POST /api/impersonate/exit}, which
 * deliberately is not — see {@link #EXIT_URL}.
 */
@Slf4j
@Configuration
public class ImpersonationConfig {

    public static final String SWITCH_URL = "/api/admin/impersonate";
    /**
     * Deliberately <em>not</em> under {@code /api/admin/**}.
     *
     * <p>While a switch is active the session no longer carries {@code ROLE_ADMIN} — it carries the
     * target's roles. An exit endpoint behind the admin rule would therefore be closed to exactly
     * the session that needs it, and the admin would be stuck as the other user until the session
     * expired. It is guarded by {@code ROLE_PREVIOUS_ADMINISTRATOR} instead, the authority
     * {@link SwitchUserFilter} grants while switched, which is available only to a switched
     * session and to no one else.
     */
    public static final String EXIT_URL = "/api/impersonate/exit";

    /** The authority {@code SwitchUserFilter} grants for the duration of a switch. */
    public static final String SWITCHED_AUTHORITY = "ROLE_PREVIOUS_ADMINISTRATOR";

    @Bean
    public SwitchUserFilter switchUserFilter(UserDetailsService userDetailsService) {
        final var filter = new SwitchUserFilter();
        filter.setUserDetailsService(userDetailsService);
        filter.setSwitchUserUrl(SWITCH_URL);
        filter.setExitUserUrl(EXIT_URL);
        filter.setUserDetailsChecker(refuseAdminTargets());
        filter.setSuccessHandler(logAndAnswerNoContent());
        filter.setFailureHandler(answerForbidden());
        return filter;
    }

    /**
     * Refuses to impersonate another ADMIN.
     *
     * <p>An admin acting as another admin can use those rights without the action being traceable
     * to them — a privilege escalation that leaves no trace (ADR-0020). The check sits on the
     * filter's {@code UserDetailsChecker}, which runs where the target account is loaded, rather
     * than in the UI: a rule enforced only by a hidden button is not enforced.
     */
    static UserDetailsChecker refuseAdminTargets() {
        return user -> {
            final boolean targetIsAdmin = user.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch("ROLE_ADMIN"::equals);
            if (targetIsAdmin) {
                log.warn("Refused impersonation of admin account '{}'", user.getUsername());
                throw new ImpersonationNotAllowedException(
                        "Administrators cannot be impersonated: " + user.getUsername());
            }
        };
    }

    /**
     * Answers {@code 204} instead of redirecting, and logs both directions.
     *
     * <p>The SPA calls this with {@code fetch}; a redirect would be followed and hand it a page of
     * HTML. The log entries are on {@code warn} on purpose — not because something went wrong, but
     * because an entry below the usual threshold is not there when it is needed. Without both ends
     * recorded, nobody can later tell whether a user did something themselves (ADR-0020).
     */
    static AuthenticationSuccessHandler logAndAnswerNoContent() {
        return (request, response, authentication) -> {
            originalUsername(authentication).ifPresentOrElse(
                    admin -> log.warn("Impersonation STARTED: admin '{}' is now acting as '{}'",
                            admin, authentication.getName()),
                    () -> log.warn("Impersonation ENDED: back to '{}'", authentication.getName()));
            response.setStatus(HttpStatus.NO_CONTENT.value());
        };
    }

    private static AuthenticationFailureHandler answerForbidden() {
        return (request, response, exception) ->
                response.sendError(HttpStatus.FORBIDDEN.value(), exception.getMessage());
    }

    /**
     * The admin behind an impersonation, or empty when the authentication is the caller's own.
     *
     * <p>{@link SwitchUserGrantedAuthority} is where {@code SwitchUserFilter} parks the original
     * authentication, and its presence is the only reliable marker that a switch is active.
     */
    public static Optional<String> originalUsername(Authentication authentication) {
        if (authentication == null) {
            return Optional.empty();
        }
        return authentication.getAuthorities().stream()
                .filter(SwitchUserGrantedAuthority.class::isInstance)
                .map(SwitchUserGrantedAuthority.class::cast)
                .map(authority -> authority.getSource().getName())
                .findFirst();
    }

    /** Signals a target account that may not be impersonated; mapped to {@code 403}. */
    public static class ImpersonationNotAllowedException
            extends org.springframework.security.core.AuthenticationException {
        public ImpersonationNotAllowedException(String message) {
            super(message);
        }
    }
}
