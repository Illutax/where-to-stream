package tech.dobler.werstreamt.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.time.Duration;
import java.util.UUID;

/**
 * Application security: form + HTTP Basic + (optional) Google OIDC login over a database-backed
 * user store, with role-based authorization.
 *
 * <p>Everything requires authentication; state-changing / maintenance endpoints and the user
 * administration require {@code ADMIN}. CSRF uses a cookie repository so the Angular SPA (session
 * cookie) and the Thymeleaf forms are both protected; {@code /api/**} returns 401 instead of a
 * login redirect so the SPA can react.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Slf4j
public class SecurityConfig {

    /** Matches API requests (context-path aware) — used to answer with 401 instead of a redirect. */
    private static final RequestMatcher API = request ->
            request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/");

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ObjectProvider<ClientRegistrationRepository> clientRegistrations,
                                                   GoogleOidcUserService oidcUserService,
                                                   SecurityProperties securityProperties) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Public: login, errors, static assets for the login page, status probe.
                        .requestMatchers("/login", "/error", "/favicon.ico").permitAll()
                        .requestMatchers("/public/**", "/webjars/**", "/css/**", "/js/**").permitAll()
                        // ADMIN: user administration.
                        .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                        // ADMIN: state-changing / maintenance endpoints (legacy + API) — fixes TODO-5.
                        .requestMatchers("/pre-cache", "/check-pre-cache", "/refresh/**",
                                "/invalidate", "/scrape-invalidated", "/manage").hasRole("ADMIN")
                        .requestMatchers("/api/manage/**", "/api/cache/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/refresh").hasRole("ADMIN")
                        // Everything else — read pages, GET /api/**, /watchlist (import my own list),
                        // /api/watchlist/**, the SPA at /app/** — just needs a login.
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").permitAll())
                .httpBasic(Customizer.withDefaults())
                // Persistent login (survives browser close AND server restarts, given a stable key)
                // so users are not logged out on each cron-driven redeploy.
                .rememberMe(rm -> rm
                        .key(rememberMeKey(securityProperties))
                        .tokenValiditySeconds((int) Duration.ofDays(securityProperties.rememberMe().validityDays()).toSeconds()))
                .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll())
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), API))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);

        // OIDC login is wired only when a client registration is actually configured (e.g. Google
        // client-id/secret present), so the app still starts and tests run without an IdP.
        if (clientRegistrations.getIfAvailable() != null) {
            http.oauth2Login(oauth -> oauth
                    .loginPage("/login")
                    .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService)));
        }

        return http.build();
    }

    /**
     * The stable remember-me secret. If none is configured, a transient one is generated per
     * startup (so remember-me resets on each restart) and a warning is logged — set
     * {@code w2s.security.remember-me.key} to keep users logged in across restarts.
     */
    private String rememberMeKey(SecurityProperties securityProperties) {
        final String key = securityProperties.rememberMe().key();
        if (key != null && !key.isBlank()) {
            return key;
        }
        log.warn("No w2s.security.remember-me.key configured — generated a transient key; "
                + "remember-me logins will NOT survive restarts. Set the property for persistent login.");
        return UUID.randomUUID().toString();
    }
}
