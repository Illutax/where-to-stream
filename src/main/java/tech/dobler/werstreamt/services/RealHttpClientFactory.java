package tech.dobler.werstreamt.services;

import org.springframework.stereotype.Component;

import java.net.http.HttpClient;

/** The production {@link HttpClientFactory}: a fresh, properly configured client per call. */
@Component
class RealHttpClientFactory implements HttpClientFactory {
    @Override
    public HttpClient newClient() {
        return OutboundHttpClients.newClient();
    }
}
