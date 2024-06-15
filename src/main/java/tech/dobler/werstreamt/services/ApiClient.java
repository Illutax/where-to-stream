package tech.dobler.werstreamt.services;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import tech.dobler.werstreamt.domainvalues.AvailabilityType;
import tech.dobler.werstreamt.domainvalues.Price;
import tech.dobler.werstreamt.entities.Availability;
import tech.dobler.werstreamt.entities.QueryResult;
import tech.dobler.werstreamt.domainvalues.SearchResult;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class ApiClient {
    private final URI baseUrl = URI.create("https://www.werstreamt.es/filme/");

    public List<SearchResult> search(String searchTerm) {
        log.info("Searching for: " + searchTerm);
        final var query = UriComponentsBuilder.fromUri(baseUrl).queryParam("q", searchTerm).build();
        final var connect = getConnectionWithUserAgent(query);
        try {
            final var document = connect.get();
            final var elements = document.select(".results > ul > li[data-contentid]");
            return elements.stream()
                    .map(e -> {
                        final var name = e.selectFirst("strong").childNode(0).toString();
                        final var url = e.selectFirst("a").attr("href");
                        return new SearchResult(name, URI.create("https://www.werstreamt.es/" + url));
                    }).toList();
        } catch (HttpStatusException e) {
            log.error("Not found %s".formatted(e.getMessage()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return List.of();
    }

    public List<QueryResult> query(String imdbId) {
        log.info("Query with id: {}", imdbId);

        final var query = UriComponentsBuilder.fromUri(baseUrl).queryParam("q", imdbId).queryParam("action_results", "suchen").build();
        final var connect = getConnectionWithUserAgent(query).followRedirects(true);
        try {
            final var document = connect.get();
            final var elements = document.select("#avalibility > .provider");

            return elements.stream()
                    .flatMap(e -> {
                        final var name = e.attr("data-ext-provider-name");
                        final var columns = e.select(".columns.small-4");
                        final var flatrate = columns.get(0).selectFirst(".fi-check") != null;
                        final var e1 = parseAvailability(columns.get(1), AvailabilityType.RENT);
                        final var e2 = parseAvailability(columns.get(2), AvailabilityType.BUY);
                        if (columns.size() == 3) {
                            return Stream.of(new QueryResult(imdbId, name, flatrate,
                                    Stream.of(e1, e2).filter(Optional::isPresent).map(Optional::get).toList()));
                        } else if (columns.size() == 6) {
                            final var f2 = columns.get(3).selectFirst(".fi-check") != null;
                            final var e3 = parseAvailability(columns.get(4), AvailabilityType.RENT);
                            final var e4 = parseAvailability(columns.get(5), AvailabilityType.BUY);
                            return Stream.of(new QueryResult(imdbId, name + "(1)", flatrate,
                                            Stream.of(e1, e2).filter(Optional::isPresent).map(Optional::get).toList()),
                                    new QueryResult(imdbId, name + "(2)", f2,
                                            Stream.of(e3, e4).filter(Optional::isPresent).map(Optional::get).toList()));
                        } else {
                            log.error("Got something unexpected. for id {} \n{}", imdbId, columns);
                            return Stream.of();
                        }
                    })
                    .toList();
        } catch (HttpStatusException e) {
            log.error("Not found %s".formatted(e.getMessage()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        log.info("Found none for id: {}", imdbId);
        return List.of();
    }

    private static Optional<Availability> parseAvailability(Element column, AvailabilityType type) {
        final var qualities = column.select("em").stream()
                .map(e -> List.of(
                        e.childNode(0).childNode(0).toString(),
                        e.childNode(1).toString()))
                .collect(Collectors.toMap(List::getFirst, e -> e.get(1)));
        final var sd = qualities.get("SD");
        final var hd = qualities.get("HD");
        final var fourK = qualities.get("4K");
        if (sd == null && hd == null && fourK == null) {
            return Optional.empty();
        }
        return Optional.of(new Availability(type, new Price(sd), new Price(hd), new Price(fourK)));
    }

    private static Connection getConnectionWithUserAgent(UriComponents query) {
        return Jsoup.connect(query.toString())
                .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                .referrer("http://www.google.com");
    }
}
