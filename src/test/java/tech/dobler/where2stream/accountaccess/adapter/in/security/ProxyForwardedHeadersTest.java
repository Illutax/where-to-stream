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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * That the application reads {@code X-Forwarded-*} and builds its redirects from them.
 *
 * <p>Its own test class because the failure is quiet: when the headers are ignored nothing breaks
 * outright — redirects merely point at {@code http}, the proxy sends them back, and the page
 * arrives. The cost is a plaintext hop per redirect and login targets built from a scheme the
 * request never used (TODO-61).
 *
 * <p>Runs against a real port, and that is not incidental: the strategy in use is a Tomcat valve,
 * which MockMvc has no container to run. Under MockMvc every assertion here would be vacuously
 * true. Plain {@link HttpClient} rather than a Spring test client, because the redirect must not be
 * followed — the redirect is the assertion.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProxyForwardedHeadersTest {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /** A browser navigation, optionally dressed the way the proxy would present it. */
    private HttpResponse<String> navigateTo(String path, boolean behindProxy) throws Exception {
        final var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "text/html");
        if (behindProxy) {
            request.header("X-Forwarded-Proto", "https").header("X-Forwarded-Host", "w2s.example");
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String locationOf(HttpResponse<String> response) {
        return response.headers().firstValue("Location").orElse("<none>");
    }

    @Test
    void aRedirectKeepsTheSchemeAndHostTheClientActuallyUsed() throws Exception {
        // Believing the wrong scheme is how "https in, http out" happens. The target has to stay the
        // login page — only scheme and host change — or this would be "fixed" by breaking it.
        final var location = locationOf(navigateTo("/app/", true));

        assertThat(location).startsWith("https://w2s.example/").endsWith("/login");
    }

    @Test
    void withoutTheHeadersTheClientIsToldTheTruth() throws Exception {
        // Direct access — a probe on the internal network, a developer on localhost — must not be
        // told it is on https just because production is.
        final var location = locationOf(navigateTo("/app/", false));

        assertThat(location).startsWith("http://localhost:" + port).endsWith("/login");
    }

    @Test
    void anAuthenticatedApiCallIsAnsweredNormallyBehindTheProxy() throws Exception {
        // The valve rewrites what the application believes the request was; it must not turn an
        // ordinary API answer into a redirect or otherwise disturb it.
        final var credentials = Base64.getEncoder()
                .encodeToString("admin:admin-test-pw".getBytes(StandardCharsets.UTF_8));
        final var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/me"))
                .header("Authorization", "Basic " + credentials)
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "w2s.example")
                .build();

        final var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
    }
}
