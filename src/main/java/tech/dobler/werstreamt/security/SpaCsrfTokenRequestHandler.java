package tech.dobler.werstreamt.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * CSRF handling for a cookie-based SPA (Angular) alongside server-rendered forms — the pattern
 * from the Spring Security reference ("Integrating with SPAs"):
 *
 * <ul>
 *   <li>Rendering uses the BREACH-protecting {@link XorCsrfTokenRequestAttributeHandler} (so the
 *       token in the {@code XSRF-TOKEN} cookie / Thymeleaf form is masked).</li>
 *   <li>Resolution accepts the raw token from the {@code X-XSRF-TOKEN} header that Angular sends
 *       (plain handler), and falls back to the XOR handler for form posts.</li>
 * </ul>
 */
final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        // Render the masked token into the request attribute / cookie.
        this.xor.handle(request, response, csrfToken);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        // Header (SPA) -> raw value; body/param (form) -> XOR-masked value.
        final String headerValue = request.getHeader(csrfToken.getHeaderName());
        return (StringUtils.hasText(headerValue) ? this.plain : this.xor)
                .resolveCsrfTokenValue(request, csrfToken);
    }
}
