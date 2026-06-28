package tech.dobler.werstreamt.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import tech.dobler.werstreamt.domain.AvailabilityType;
import tech.dobler.werstreamt.domain.Price;
import tech.dobler.werstreamt.domain.Availability;
import tech.dobler.werstreamt.domain.QueryResult;
import tech.dobler.werstreamt.domain.SearchResult;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class WerStreamtEsApiClient implements StreamAvailabilityProvider {
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

    @Override
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

    /** One listing of a provider: its flatrate/rent/buy prices plus the language variant. */
    private record Offering(boolean flatrate, List<Availability> availabilities, String languages) {
    }

    /**
     * Parses a provider block into one {@link QueryResult} per <em>distinct</em> listing.
     * A provider may list the same title several times (e.g. different languages); identical
     * listings (same flatrate + prices + languages) are merged. When more than one distinct
     * listing remains, each keeps its {@code languages} so callers can tell them apart;
     * a single listing keeps {@code languages == null} (no differentiator needed). Malformed
     * markup is logged and skipped so one bad provider never aborts the whole parse.
     */
    private List<QueryResult> parseProvider(Element provider, String imdbId) {
        final var name = provider.attr("data-ext-provider-name");
        try {
            final var offerings = parseOfferings(provider);
            if (offerings.isEmpty()) {
                log.warn("No parseable offering for id {} provider '{}'", imdbId, name);
                return List.of();
            }
            final var distinct = new ArrayList<>(new LinkedHashSet<>(offerings));
            final boolean single = distinct.size() == 1;
            return distinct.stream()
                    .map(o -> new QueryResult(imdbId, name, o.flatrate(), o.availabilities(),
                            single ? null : o.languages()))
                    .toList();
        } catch (RuntimeException ex) {
            log.error("Failed to parse provider '{}' for id {}: {}", name, imdbId, ex.toString());
            return List.of();
        }
    }

    private List<Offering> parseOfferings(Element provider) {
        final var rows = provider.select(".panel.available");
        if (!rows.isEmpty()) {
            return rows.stream().map(WerStreamtEsApiClient::parseOfferingRow).filter(Objects::nonNull).toList();
        }
        // Fallback for the flat layout (no per-listing rows): chunk the columns into groups of 3.
        final var columns = provider.select(".columns.small-4");
        if (columns.isEmpty() || columns.size() % 3 != 0) {
            return List.of();
        }
        final var offerings = new ArrayList<Offering>();
        for (int i = 0; i < columns.size(); i += 3) {
            offerings.add(new Offering(hasCheck(columns.get(i)),
                    availabilities(columns.get(i + 1), columns.get(i + 2)), null));
        }
        return offerings;
    }

    private static Offering parseOfferingRow(Element offering) {
        final var columns = offering.select(".columns.small-4");
        if (columns.size() < 3) {
            return null;
        }
        return new Offering(hasCheck(columns.get(0)),
                availabilities(columns.get(1), columns.get(2)),
                extractLanguages(offering));
    }

    /** Language list from a listing's title block ("87 Min. | Deutsch, Englisch (OV)"), or null. */
    private static String extractLanguages(Element offering) {
        final var titleCol = offering.selectFirst(".columns.large-5");
        if (titleCol == null) {
            return null;
        }
        final var holder = titleCol.selectFirst("button");
        return (holder != null ? holder : titleCol).childNodes().stream()
                .filter(TextNode.class::isInstance)
                .map(node -> ((TextNode) node).text().trim())
                .filter(text -> text.contains("|"))
                .findFirst()
                .map(text -> text.substring(text.indexOf('|') + 1).trim())
                .filter(languages -> !languages.isBlank())
                .orElse(null);
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
