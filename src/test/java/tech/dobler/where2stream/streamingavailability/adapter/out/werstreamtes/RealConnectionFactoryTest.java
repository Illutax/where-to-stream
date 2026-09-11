package tech.dobler.where2stream.streamingavailability.adapter.out.werstreamtes;

import org.jsoup.Connection;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RealConnectionFactoryTest {

    private static final WerStreamtProperties PROPS = new WerStreamtProperties(
            new WerStreamtProperties.Invalidate(28, 1.5, 2.0), new WerStreamtProperties.RateLimit(0),
            new WerStreamtProperties.BackgroundRefresh(true, "0 0 4 * * *"), Duration.ofSeconds(7));

    @Test
    void buildsAJsoupConnectionForTheGivenUriWithTheConfiguredTimeout() {
        final var uri = UriComponentsBuilder.fromUriString("https://www.werstreamt.es/filme/").build();

        final var connection = new RealConnectionFactory(PROPS).connectionFor(uri);

        assertThat(connection.request())
                .extracting(request -> request.url().toString(), Connection.Request::timeout)
                .containsExactly(uri.toString(), 7_000);
    }
}
