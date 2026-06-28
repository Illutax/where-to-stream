package tech.dobler.werstreamt.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.werstreamt.configurations.WerStreamtProperties;
import tech.dobler.werstreamt.entities.ImdbEntry;
import tech.dobler.werstreamt.entities.QueryResult;
import tech.dobler.werstreamt.persistence.QueryMeta;
import tech.dobler.werstreamt.persistence.QueryMetaRepository;
import tech.dobler.werstreamt.persistence.QueryResultDB;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreamInfoServiceTest {

    private static final WerStreamtProperties PROPS =
            new WerStreamtProperties("assets", new WerStreamtProperties.Invalidate(28));

    @Mock
    private WerStreamtEsApiClient werStreamtEsApiClient;
    @Mock
    private ImdbEntryRepository imdbEntryRepository;
    @Mock
    private QueryMetaRepository queryMetaRepository;

    private StreamInfoService service;

    @BeforeEach
    void setUp() {
        service = new StreamInfoService(werStreamtEsApiClient, imdbEntryRepository, queryMetaRepository, PROPS);
    }

    private static ImdbEntry entry(String imdbId) {
        return new ImdbEntry(1, "movie", URI.create("https://www.imdb.com/title/" + imdbId + "/"),
                "2020-01-01", false, 2020, imdbId);
    }

    private static QueryMeta meta(String imdbId, Instant creationTime, String serviceName) {
        return QueryMeta.of(imdbId, creationTime, List.of(new QueryResultDB(imdbId, serviceName, true, List.of())));
    }

    private void stubFindFirst(String imdbId, Optional<QueryMeta> result) {
        when(queryMetaRepository.findFirstByImdbIdAndInvalidatedIsFalseOrderByCreationTimeDesc(imdbId))
                .thenReturn(result);
    }

    @Test
    void resolveReturnsCachedResultWhenFresh() {
        stubFindFirst("tt1", Optional.of(meta("tt1", Instant.now(), "Netflix")));

        final var result = service.resolve("tt1");

        assertThat(result).extracting(QueryResult::streamingServiceName).containsExactly("Netflix");
        verifyNoInteractions(werStreamtEsApiClient);
        verify(queryMetaRepository, never()).save(any());
    }

    @Test
    void resolveFetchesAndCachesOnMiss() {
        stubFindFirst("tt2", Optional.empty());
        when(imdbEntryRepository.findByImdb("tt2")).thenReturn(Optional.of(entry("tt2")));
        final var fetched = new QueryResult("tt2", "Prime Video", false, List.of());
        when(werStreamtEsApiClient.query("tt2")).thenReturn(List.of(fetched));

        final var result = service.resolve("tt2");

        assertThat(result).containsExactly(fetched);
        verify(werStreamtEsApiClient).query("tt2");
        verify(queryMetaRepository).save(any(QueryMeta.class));
    }

    @Test
    void resolveRefetchesWhenCacheExpired() {
        stubFindFirst("tt3", Optional.of(meta("tt3", Instant.now().minus(40, ChronoUnit.DAYS), "Stale")));
        when(imdbEntryRepository.findByImdb("tt3")).thenReturn(Optional.of(entry("tt3")));
        when(werStreamtEsApiClient.query("tt3")).thenReturn(List.of(new QueryResult("tt3", "Fresh", false, List.of())));

        final var result = service.resolve("tt3");

        assertThat(result).extracting(QueryResult::streamingServiceName).containsExactly("Fresh");
        verify(werStreamtEsApiClient).query("tt3");
    }

    @Test
    void resolveForceRefreshAlwaysFetches() {
        stubFindFirst("tt4", Optional.of(meta("tt4", Instant.now(), "Cached")));
        when(imdbEntryRepository.findByImdb("tt4")).thenReturn(Optional.of(entry("tt4")));
        when(werStreamtEsApiClient.query("tt4")).thenReturn(List.of(new QueryResult("tt4", "Refreshed", false, List.of())));

        final var result = service.resolve("tt4", true);

        assertThat(result).extracting(QueryResult::streamingServiceName).containsExactly("Refreshed");
        verify(werStreamtEsApiClient).query("tt4");
    }

    @Test
    void resolveAllReadsCacheInOneQueryAndFetchesOnlyMisses() {
        when(queryMetaRepository.findByImdbIdInAndInvalidatedIsFalse(List.of("tt1", "tt2")))
                .thenReturn(List.of(meta("tt1", Instant.now(), "Netflix")));
        // tt2 is a cache miss -> fetched individually
        when(imdbEntryRepository.findByImdb("tt2")).thenReturn(Optional.of(entry("tt2")));
        when(werStreamtEsApiClient.query("tt2")).thenReturn(List.of(new QueryResult("tt2", "Prime Video", false, List.of())));

        final var result = service.resolveAll(List.of("tt1", "tt2"));

        assertThat(result.get("tt1")).extracting(QueryResult::streamingServiceName).containsExactly("Netflix");
        assertThat(result.get("tt2")).extracting(QueryResult::streamingServiceName).containsExactly("Prime Video");
        verify(queryMetaRepository).findByImdbIdInAndInvalidatedIsFalse(List.of("tt1", "tt2"));
        verify(werStreamtEsApiClient).query("tt2");
        verify(werStreamtEsApiClient, never()).query("tt1");
    }
}
