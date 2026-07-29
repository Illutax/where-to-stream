package tech.dobler.where2stream.streamingavailability.adapter.out.werstreamtes;

import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class RealConnectionFactoryTest {

    @Test
    void buildsAJsoupConnectionForTheGivenUri() {
        final var uri = UriComponentsBuilder.fromUriString("https://www.werstreamt.es/filme/").build();

        final var connection = new RealConnectionFactory().connectionFor(uri);

        assertThat(connection.request().url().toString()).isEqualTo(uri.toString());
    }
}
