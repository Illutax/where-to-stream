package tech.dobler.werstreamt.security;

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
                                                   GoogleOidcUserService oidcUserService) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Public: login, errors, static assets for the login page, status probe.
                        .requestMatchers("/login", "/error", "/favicon.ico").permitAll()
                        .requestMatchers("/public/**", "/webjars/**", "/css/**", "/js/**").permitAll()
                        // ADMIN: user administration.
                        .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                        // ADMIN: state-changing / maintenance endpoints (legacy + API) — fixes TODO-5.
                        .requestMatchers("/pre-cache", "/check-pre-cache", "/refresh/**",
                                "/invalidate", "/scrape-invalidated", "/list", "/list-change", "/manage").hasRole("ADMIN")
                        .requestMatchers("/api/manage/**", "/api/cache/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/refresh").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/lists/selection").hasRole("ADMIN")
                        // Everything else (read pages, GET /api/**, the SPA at /app/**) needs a login.
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").permitAll())
                .httpBasic(Customizer.withDefaults())
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
}
