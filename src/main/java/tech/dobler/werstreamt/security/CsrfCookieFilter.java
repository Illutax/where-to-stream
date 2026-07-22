package tech.dobler.werstreamt.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Eagerly loads the {@link CsrfToken} so its cookie is written on every response — otherwise the
 * {@code XSRF-TOKEN} cookie the SPA needs would only appear lazily, after the first protected
 * request. Part of the Spring Security SPA CSRF pattern.
 */
final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        final CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken(); // triggers the deferred token to load and the cookie to be set
        }
        filterChain.doFilter(request, response);
    }
}
