package tech.dobler.where2stream.streamingavailability.adapter.out.werstreamtes;

import org.jsoup.Connection;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponents;

/**
 * The production {@link ConnectionFactory}: a real jsoup connection with the shared User-Agent
 * and the configured request timeout ({@code wer-streamt.timeout}) — the JSON adapters bound
 * every outbound call, and the scrape must not be the one integration that can hang a request
 * thread for jsoup's default instead.
 */
@Component
class RealConnectionFactory implements ConnectionFactory {
    private final WerStreamtProperties properties;

    RealConnectionFactory(WerStreamtProperties properties) {
        this.properties = properties;
    }

    @Override
    public Connection connectionFor(UriComponents uri) {
        return ApiClientUtils.getConnectionWithUserAgent(uri)
                .timeout((int) properties.timeout().toMillis());
    }
}
