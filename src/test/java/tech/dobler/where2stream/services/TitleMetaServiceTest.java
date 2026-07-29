package tech.dobler.where2stream.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import tech.dobler.where2stream.configurations.PosterProperties;
import tech.dobler.where2stream.domain.AgeRating;
import tech.dobler.where2stream.domain.AgeRating.RatingSystem;
import tech.dobler.where2stream.shared.domain.ImdbId;
import tech.dobler.where2stream.persistence.TitleMeta;
import tech.dobler.where2stream.persistence.TitleMetaRepository;
import tech.dobler.where2stream.services.ImdbTitleClient.ImdbTitleData;
import tech.dobler.where2stream.shared.time.TimeService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TitleMetaServiceTest {

    private static final ImdbId TT = ImdbId.of("tt0068646");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private TitleMetaRepository repository;
    @Mock
    private ImdbTitleClient client;
    @Mock
    private TimeService timeService;
    @Mock
    private ObjectProvider<TitleMetaService> self;

    private TitleMetaService service() {
        lenient().when(timeService.now()).thenReturn(NOW);
        lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        final var svc = new TitleMetaService(repository, client, new PosterProperties(14), timeService, self);
        lenient().when(self.getObject()).thenReturn(svc);
        return svc;
    }

    @Test
    void servesCachedMetadataWithoutFetching() {
        when(repository.findByImdbId(TT)).thenReturn(Optional.of(
                TitleMeta.of(TT, "/p.jpg", RatingSystem.FSK, "16", "Der Pate", NOW)));

        final var data = service().get(TT);

        assertThat(data).get()
                .extracting(ImdbTitleData::posterUrl, ImdbTitleData::rating, ImdbTitleData::germanTitle)
                .containsExactly("/p.jpg", AgeRating.fsk("16"), "Der Pate");
        verifyNoInteractions(client);
    }

    @Test
    void onMissFetchesOnceAndStores() {
        when(repository.findByImdbId(TT)).thenReturn(Optional.empty());
        final var fetched = new ImdbTitleData("/p.jpg", AgeRating.fsk("12"), "Oben");
        when(client.fetch(TT)).thenReturn(Optional.of(fetched));

        assertThat(service().get(TT)).contains(fetched);
        verify(client).fetch(TT);
        verify(repository).save(any(TitleMeta.class));
    }

    @Test
    void servesAGermanTitleOnlyRowAsAPositiveHit() {
        // A title with no poster/rating but a German title is still a positive (permanent) result.
        when(repository.findByImdbId(TT)).thenReturn(Optional.of(TitleMeta.of(TT, null, null, null, "Oben", NOW)));

        assertThat(service().get(TT)).get().extracting(ImdbTitleData::germanTitle).isEqualTo("Oben");
        verifyNoInteractions(client);
    }

    @Test
    void honoursTheFreshNegativeCacheForEverything() {
        when(repository.findByImdbId(TT)).thenReturn(Optional.of(TitleMeta.of(TT, null, null, null, null, NOW)));

        final var service = service();
        assertThat(service.posterPath(TT)).isEmpty();
        final var data = service.get(TT);
        assertThat(data).isPresent();
        assertThat(data).get().extracting(ImdbTitleData::rating, ImdbTitleData::germanTitle).containsOnlyNulls();
        verifyNoInteractions(client);
    }

    @Test
    void reFetchesAStaleNegative() {
        final var stale = TitleMeta.of(TT, null, null, null, null, NOW.minus(30, ChronoUnit.DAYS));
        when(repository.findByImdbId(TT)).thenReturn(Optional.of(stale));
        when(client.fetch(TT)).thenReturn(Optional.of(new ImdbTitleData("/p.jpg", null, null)));

        assertThat(service().posterPath(TT)).contains("/p.jpg");
        verify(client).fetch(TT);
    }

    @Test
    void doesNotCacheAHardFailure() {
        when(repository.findByImdbId(TT)).thenReturn(Optional.empty());
        when(client.fetch(TT)).thenReturn(Optional.empty());

        assertThat(service().get(TT)).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void swallowsAConcurrentInsertAndStillReturnsTheData() {
        final var service = service();
        when(repository.findByImdbId(TT)).thenReturn(Optional.empty());
        final var fetched = new ImdbTitleData("/p.jpg", AgeRating.fsk("16"), "Der Pate");
        when(client.fetch(TT)).thenReturn(Optional.of(fetched));
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("Duplicate entry for key 'imdb_id'"));

        assertThat(service.get(TT)).contains(fetched);
    }
}
