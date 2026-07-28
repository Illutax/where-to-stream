package tech.dobler.werstreamt.services;

import org.jsoup.Connection;
import org.springframework.web.util.UriComponents;

/**
 * Produces a jsoup {@link Connection} for a given URI, for {@link WerStreamtEsApiClient}. Unlike
 * {@link HttpClientFactory}, this is a genuine per-request factory: a jsoup {@link Connection} is
 * single-use and carries the target URI, so there's no reusable client object to cache. Exists so
 * tests can inject a fake/mocked {@link Connection} instead of hitting the real site.
 */
interface ConnectionFactory {
    Connection connectionFor(UriComponents uri);
}
