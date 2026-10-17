package tech.dobler.where2stream.accountaccess.adapter.in.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * That the application reads {@code X-Forwarded-*} and believes them over what the socket says.
 *
 * <p>Its own test class because the failure is quiet: when the headers are ignored nothing breaks
 * outright — the login redirect points at {@code http}, the proxy sends it back, and the page
 * arrives. The cost is a plaintext hop and a session cookie that loses its {@code Secure} flag
 * (TODO-61).
 *
 * <p>Runs against a real port, and that is not incidental: the strategy in use is a Tomcat valve,
 * which MockMvc has no container to run. Under MockMvc every assertion here would be vacuously
 * true. Plain {@link HttpClient} rather than a Spring test client, because the redirect must not be
 * followed — the redirect is the assertion.
 *
 * <h2>Why the test logs in rather than just asking for a protected page</h2>
 *
 * <p>Two signals survive {@code server.tomcat.servlet.use-relative-redirects}, and the test is
 * built on those two deliberately (ADR-0023). The entry-point redirect to {@code /login} does not:
 * under the Spring Boot 4.2 default it is the bare path {@code /login}, which carries no scheme and
 * no host to be right or wrong about. Asserting on it would pin the test to one side of that
 * default rather than to the behaviour we care about.
 *
 * <ul>
 *   <li>The <b>redirect after a successful login</b> is absolute either way, because Spring
 *       Security hands {@code sendRedirect} a URL it has already built in full from the saved
 *       request — and therefore from whatever the valve rewrote the request to be. This is also
 *       the exact flow TODO-61 was opened for, so the test now asserts the thing that went wrong
 *       rather than a cheaper stand-in.</li>
 *   <li>The <b>{@code Secure} flag on the session cookie</b> follows {@code request.isSecure()},
 *       which the valve sets from {@code X-Forwarded-Proto}. Tomcat's redirect handling never
 *       touches it.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProxyForwardedHeadersTest {

    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin-test-pw";

    @LocalServerPort
    private int port;

    /**
     * A browser, optionally dressed the way the proxy would present it.
     *
     * <p>Keeps its own cookie jar rather than using {@link java.net.CookieManager}: behind the
     * proxy headers the session cookie comes back marked {@code Secure}, and a conforming cookie
     * store would then refuse to send it back over the plaintext hop this test actually speaks.
     */
    private final class Browser {

        private final boolean behindProxy;
        private final Map<String, String> cookies = new LinkedHashMap<>();
        private final HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        private Browser(boolean behindProxy) {
            this.behindProxy = behindProxy;
        }

        private HttpResponse<String> send(String path, String formBody) throws Exception {
            final var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Accept", "text/html");
            if (behindProxy) {
                request.header("X-Forwarded-Proto", "https").header("X-Forwarded-Host", "w2s.example");
            }
            if (!cookies.isEmpty()) {
                request.header("Cookie", cookies.entrySet().stream()
                        .map(cookie -> cookie.getKey() + "=" + cookie.getValue())
                        .reduce((one, other) -> one + "; " + other).orElseThrow());
            }
            // The SPA's CSRF token travels in a header, where it is read unmasked — see
            // SpaCsrfTokenRequestHandler. Simpler here than parsing the masked one out of the form.
            final var csrfToken = cookies.get("XSRF-TOKEN");
            if (csrfToken != null) {
                request.header("X-XSRF-TOKEN", csrfToken);
            }
            if (formBody == null) {
                request.GET();
            } else {
                request.header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8));
            }

            final var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            response.headers().allValues("Set-Cookie").forEach(this::remember);
            return response;
        }

        private void remember(String setCookie) {
            final var pair = setCookie.split(";", 2)[0];
            final var separator = pair.indexOf('=');
            final var name = pair.substring(0, separator);
            final var value = pair.substring(separator + 1);
            if (value.isEmpty()) {
                cookies.remove(name);
            } else {
                cookies.put(name, value);
            }
        }

        /**
         * Where the application sends the browser once it is logged in.
         *
         * <p>The unauthenticated hop first is what makes this meaningful: it is what parks the
         * saved request that the redirect is then built from (and what hands out the CSRF cookie).
         */
        private String redirectAfterLoggingIn() throws Exception {
            send("/app/", null);
            final var response = send("/login", "username=" + USERNAME + "&password=" + PASSWORD);
            return response.headers().firstValue("Location").orElse("<none>");
        }

        /** The raw {@code Set-Cookie} line for the session, attributes and all. */
        private String sessionCookieLine() throws Exception {
            return send("/app/", null).headers().allValues("Set-Cookie").stream()
                    .filter(cookie -> cookie.startsWith("SESSION="))
                    .findFirst()
                    .orElse("<none>");
        }
    }

    @Test
    void theRedirectAfterLoginKeepsTheSchemeAndHostTheClientActuallyUsed() throws Exception {
        // Believing the wrong scheme is how "https in, http out" happens. The target has to stay
        // the app — only scheme and host change — or this would be "fixed" by breaking it.
        final var location = new Browser(true).redirectAfterLoggingIn();

        assertThat(location).startsWith("https://w2s.example/app/");
    }

    @Test
    void withoutTheHeadersTheClientIsToldTheTruth() throws Exception {
        // Direct access — a probe on the internal network, a developer on localhost — must not be
        // told it is on https just because production is.
        final var location = new Browser(false).redirectAfterLoggingIn();

        assertThat(location).startsWith("http://localhost:" + port + "/app/");
    }

    @Test
    void theSessionCookieIsSecureOnlyWhenTheRequestReallyWas() throws Exception {
        // The second thing the valve decides, and the one with teeth: a session cookie without
        // Secure is one the browser will also send over plain http.
        final var behindProxy = new Browser(true).sessionCookieLine();
        final var direct = new Browser(false).sessionCookieLine();

        assertThat(List.of(behindProxy.contains("Secure"), direct.contains("Secure")))
                .as("Secure behind the proxy, then direct — [%s] / [%s]", behindProxy, direct)
                .isEqualTo(List.of(true, false));
    }

    @Test
    void anAuthenticatedApiCallIsAnsweredNormallyBehindTheProxy() throws Exception {
        // The valve rewrites what the application believes the request was; it must not turn an
        // ordinary API answer into a redirect or otherwise disturb it.
        final var credentials = Base64.getEncoder()
                .encodeToString((USERNAME + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
        final var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/me"))
                .header("Authorization", "Basic " + credentials)
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "w2s.example")
                .build();

        final var response = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
    }
}
