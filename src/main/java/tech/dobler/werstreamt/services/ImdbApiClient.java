package tech.dobler.werstreamt.services;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import tech.dobler.werstreamt.domainvalues.SearchResult;

import java.io.IOException;
import java.net.URI;
import java.util.List;

@Slf4j
public class ImdbApiClient {
    private final URI baseUrl = URI.create("https://www.imdb.com/list/");

    public List<SearchResult> search(String listId) {
        log.info("Searching for: " + listId);
        final var query = UriComponentsBuilder.fromUri(baseUrl).pathSegment(listId).build();
        final var connect = ApiClientUtils.getConnectionWithUserAgent(query);
        try {
            final var document = connect.get();
            return null;
        } catch (HttpStatusException e) {
            log.error("Not found %s".formatted(e.getMessage()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return List.of();
    }
}
