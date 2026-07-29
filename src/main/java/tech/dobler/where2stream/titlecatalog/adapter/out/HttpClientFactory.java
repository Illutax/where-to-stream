package tech.dobler.where2stream.titlecatalog.adapter.out;

import java.net.http.HttpClient;

/**
 * Produces the {@link HttpClient} used by the outbound JSON/REST integrations
 * (ImdbTitleSource, ImdbPosterSource, TmdbPosterSource, ImdbSuggestionSource).
 * Each of them calls this once, at construction, and keeps the returned client for its own lifetime —
 * this is not a per-request factory,
 * just an indirection so tests can inject a fake/mocked {@link HttpClient} instead of a real one.
 * Unlike {@link HttpClient#send}, {@link #newClient()} isn't a generic method,
 * so a plain lambda (e.g. {@code () -> mockHttpClient}) works fine as a test double.
 */
public interface HttpClientFactory {
    HttpClient newClient();
}
