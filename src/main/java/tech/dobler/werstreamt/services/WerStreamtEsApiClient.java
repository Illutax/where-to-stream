package tech.dobler.werstreamt.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import tech.dobler.werstreamt.domain.AvailabilityType;
import tech.dobler.werstreamt.domain.Price;
import tech.dobler.werstreamt.domain.Availability;
import tech.dobler.werstreamt.domain.QueryResult;
import tech.dobler.werstreamt.domain.SearchResult;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class WerStreamtEsApiClient {
    private final URI baseUrl = URI.create("https://www.werstreamt.es/filme/");
    private final RateLimiter rateLimiter;

    public List<SearchResult> search(String searchTerm) {
        log.info("Searching for: {}", searchTerm);
        final var query = UriComponentsBuilder.fromUri(baseUrl).queryParam("q", searchTerm).build();
        final var connect = ApiClientUtils.getConnectionWithUserAgent(query);
        try {
            rateLimiter.acquire();
            final var document = connect.get();
            return document.select(".results > ul > li[data-contentid]").stream()
                    .map(WerStreamtEsApiClient::toSearchResult)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
        } catch (HttpStatusException e) {
            log.error("Search for '{}' failed: {}", searchTerm, e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException("Search for '%s' failed".formatted(searchTerm), e);
        }
        return List.of();
    }

    private static Optional<SearchResult> toSearchResult(Element element) {
        final var strong = element.selectFirst("strong");
        final var anchor = element.selectFirst("a");
        if (strong == null || strong.childNodeSize() == 0 || anchor == null) {
            log.warn("Skipping search result with unexpected markup");
            return Optional.empty();
        }
        final var name = strong.childNode(0).toString();
        final var url = anchor.attr("href");
        return Optional.of(new SearchResult(name, URI.create("https://www.werstreamt.es/" + url)));
    }

    public List<QueryResult> query(String imdbId) {
        log.info("Query with id: {}", imdbId);

        final var query = UriComponentsBuilder.fromUri(baseUrl).queryParam("q", imdbId).queryParam("action_results", "suchen").build();
        final var connect = ApiClientUtils.getConnectionWithUserAgent(query).followRedirects(true);
        try {
            rateLimiter.acquire();
            return parse(connect.get(), imdbId);
        } catch (HttpStatusException e) {
            log.error("Query for imdbId '{}' failed: {}", imdbId, e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException("Query for imdbId '%s' failed".formatted(imdbId), e);
        }
        log.info("Found none for id: {}", imdbId);
        return List.of();
    }

    // Package-private so the (network-free) HTML parsing can be unit tested with a fixture.
    List<QueryResult> parse(Document document, String imdbId) {
        return document.select("#avalibility > .provider").stream()
                .flatMap(provider -> parseProvider(provider, imdbId).stream())
                .toList();
    }

    /**
     * Parses a single provider block. Returns one result for the 3-column layout and two
     * results ("(1)"/"(2)") for the 6-column layout. Any unexpected layout or malformed
     * markup is logged and skipped so that one bad provider never aborts the whole parse.
     */
    private List<QueryResult> parseProvider(Element provider, String imdbId) {
        final var name = provider.attr("data-ext-provider-name");
        try {
            final var columns = provider.select(".columns.small-4");
            if (columns.size() != 3 && columns.size() != 6) {
                log.error("Unexpected column count {} for id {} provider '{}'", columns.size(), imdbId, name);
                return List.of();
            }
            final var first = new QueryResult(imdbId, columns.size() == 3 ? name : name + "(1)",
                    hasCheck(columns.get(0)),
                    availabilities(columns.get(1), columns.get(2)));
            if (columns.size() == 3) {
                return List.of(first);
            }
            final var second = new QueryResult(imdbId, name + "(2)",
                    hasCheck(columns.get(3)),
                    availabilities(columns.get(4), columns.get(5)));
            return List.of(first, second);
        } catch (RuntimeException ex) {
            log.error("Failed to parse provider '{}' for id {}: {}", name, imdbId, ex.toString());
            return List.of();
        }
    }

    private static boolean hasCheck(Element column) {
        return column.selectFirst(".fi-check") != null;
    }

    private static List<Availability> availabilities(Element rentColumn, Element buyColumn) {
        return Stream.of(parseAvailability(rentColumn, AvailabilityType.RENT),
                        parseAvailability(buyColumn, AvailabilityType.BUY))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    private static Optional<Availability> parseAvailability(Element column, AvailabilityType type) {
        final var qualities = new HashMap<String, String>();
        for (final var em : column.select("em")) {
            final var label = qualityLabel(em);
            if (label != null) {
                qualities.put(label, priceText(em));
            }
        }
        final var sd = qualities.get("SD");
        final var hd = qualities.get("HD");
        final var fourK = qualities.get("4K");
        if (sd == null && hd == null && fourK == null) {
            return Optional.empty();
        }
        return Optional.of(new Availability(type, priceOrNull(sd), priceOrNull(hd), priceOrNull(fourK)));
    }

    /** Wraps a price string, or returns {@code null} when the quality is not offered. */
    private static Price priceOrNull(String value) {
        return value == null ? null : new Price(value);
    }

    /** Quality label of an {@code <em>} (e.g. "SD"), or {@code null} if the markup differs. */
    private static String qualityLabel(Element em) {
        if (em.childNodeSize() == 0) {
            return null;
        }
        final var labelHolder = em.childNode(0);
        if (labelHolder.childNodeSize() == 0) {
            return null;
        }
        return labelHolder.childNode(0).toString();
    }

    /** Price text of an {@code <em>} (the node after the label), or {@code null} if absent. */
    private static String priceText(Element em) {
        return em.childNodeSize() < 2 ? null : em.childNode(1).toString();
    }

}
