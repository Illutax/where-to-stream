package tech.dobler.where2stream.shared.platform.outbound;

import java.net.http.HttpClient;

/**
 * Produces the {@link HttpClient} used by the outbound JSON/REST integrations
 * (ImdbTitleSource, ImdbPosterSource, TmdbPosterSource, ImdbSuggestionSource).
 * Each of them calls this once, at construction, and keeps the returned client for its own lifetime —
 * this is not a per-request factory,
 * just an indirection so tests can inject a fake/mocked {@link HttpClient} instead of a real one.
 * Unlike {@link HttpClient#send}, {@link #newClient()} isn't a generic method,
 * so a plain lambda (e.g. {@code () -> mockHttpClient}) works fine as a test double.
 *
 * <p>Lives in {@code shared.platform.outbound} rather than inside a bounded context:
 * its implementors and callers sit in different contexts, and one context may not reach into
 * another's adapters (ADR-0014).
 */
public interface HttpClientFactory {
    HttpClient newClient();
}
