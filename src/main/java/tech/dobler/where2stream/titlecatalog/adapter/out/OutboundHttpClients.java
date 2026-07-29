package tech.dobler.where2stream.titlecatalog.adapter.out;

import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Shared {@link HttpClient} construction for the outbound JSON/REST integrations (the IMDb
 * GraphQL client, the IMDb suggestion-search client, the TMDB poster source) — not used by
 * {@link WerStreamtEsApiClient}, which scrapes HTML via jsoup instead. Java's {@code HttpClient}
 * has no per-client default-header concept, so the shared {@link #USER_AGENT} still has to be
 * added per request by each caller; only the builder configuration (proxy, timeout, redirects) is
 * centralised here.
 */
public final class OutboundHttpClients {

    /** Sent as the {@code User-Agent} header by every request from these integrations. */
    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private OutboundHttpClients() {
    }

    public static HttpClient newClient() {
        return HttpClient.newBuilder()
                .proxy(ProxySelector.getDefault())
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}
