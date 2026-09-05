package tech.dobler.where2stream.accountaccess.port.in;

import org.springframework.security.core.Authentication;

import java.util.Optional;

/**
 * Whether a request is running under an admin impersonation, and who the admin is (ADR-0020).
 *
 * <p>Published because other contexts have to react to it — Purchase Offers refuses price lookups
 * while a switch is active — and because the marker lives in a Spring Security authority that
 * nobody outside Account &amp; Access should have to know about.
 *
 * <p>Takes the {@link Authentication} as an argument rather than reading the {@code SecurityContext}
 * itself: callers are controllers, and ADR-0007 keeps context reading in the presentation layer.
 */
public interface ImpersonationPort {

    /** The impersonating admin's username, or empty when the caller is acting as themselves. */
    Optional<String> impersonatingAdmin(Authentication authentication);
}
