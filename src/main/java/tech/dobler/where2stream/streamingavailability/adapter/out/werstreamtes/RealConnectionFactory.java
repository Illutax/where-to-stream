package tech.dobler.where2stream.streamingavailability.adapter.out.werstreamtes;

import org.jsoup.Connection;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponents;

/** The production {@link ConnectionFactory}: a real jsoup connection with the shared User-Agent. */
@Component
class RealConnectionFactory implements ConnectionFactory {
    @Override
    public Connection connectionFor(UriComponents uri) {
        return ApiClientUtils.getConnectionWithUserAgent(uri);
    }
}
