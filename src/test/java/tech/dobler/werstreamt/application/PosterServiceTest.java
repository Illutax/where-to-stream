package tech.dobler.werstreamt.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import tech.dobler.werstreamt.configurations.PosterProperties;
import tech.dobler.werstreamt.domain.ImdbId;
import tech.dobler.werstreamt.domain.PosterSize;
import tech.dobler.werstreamt.persistence.TitlePoster;
import tech.dobler.werstreamt.persistence.TitlePosterRepository;
import tech.dobler.werstreamt.services.PosterSource;
import tech.dobler.werstreamt.time.TimeService;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PosterServiceTest {

    private static final ImdbId TT = ImdbId.of("tt0482571");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private TitlePosterRepository repository;
    @Mock
    private PosterSource posterSource;
    @Mock
    private TimeService timeService;
    @Mock
    private ObjectProvider<PosterService> self;

    private PosterService service() {
        lenient().when(timeService.now()).thenReturn(NOW);
        lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return new PosterService(repository, posterSource, new PosterProperties(14), timeService, self);
    }

    // The public thumb()/full() are thin retry wrappers over resolve(); resolve() holds the logic,
    // so the cache behaviour is exercised through it directly (no proxy/self needed).

    @Test
    void servesTheCachedThumbnailWithoutHittingTheSource() {
        final var row = TitlePoster.of(TT, "/p.jpg", NOW);
        row.setThumb(new byte[]{1, 2, 3}, "image/jpeg");
        when(repository.findByImdbId(TT)).thenReturn(Optional.of(row));

        final var result = service().resolve(TT, PosterSize.THUMB);

        assertThat(result).get().extracting(PosterService.Poster::bytes).isEqualTo(new byte[]{1, 2, 3});
        verifyNoInteractions(posterSource);
    }

    @Test
    void onMissDiscoversThePathDownloadsAndCaches() {
        when(repository.findByImdbId(TT)).thenReturn(Optional.empty());
        when(posterSource.findPosterPath(TT)).thenReturn(Optional.of("/p.jpg"));
        when(posterSource.download("/p.jpg", PosterSize.THUMB)).thenReturn(Optional.of(new byte[]{9, 8, 7}));

        final var result = service().resolve(TT, PosterSize.THUMB);

        assertThat(result).get().extracting(PosterService.Poster::bytes).isEqualTo(new byte[]{9, 8, 7});
        verify(posterSource).findPosterPath(TT);
        verify(posterSource).download("/p.jpg", PosterSize.THUMB);
    }

    @Test
    void honoursTheNegativeCacheWithoutAskingTheSourceAgain() {
        when(repository.findByImdbId(TT)).thenReturn(Optional.of(TitlePoster.of(TT, null, NOW))); // fresh negative

        assertThat(service().resolve(TT, PosterSize.THUMB)).isEmpty();
        verify(posterSource, never()).findPosterPath(any());
    }

    @Test
    void fetchesTheFullImageOnDemandWhenOnlyTheThumbIsCached() {
        final var row = TitlePoster.of(TT, "/p.jpg", NOW);
        row.setThumb(new byte[]{1}, "image/jpeg");
        when(repository.findByImdbId(TT)).thenReturn(Optional.of(row));
        when(posterSource.download("/p.jpg", PosterSize.FULL)).thenReturn(Optional.of(new byte[]{4, 5}));

        final var result = service().resolve(TT, PosterSize.FULL);

        assertThat(result).get().extracting(PosterService.Poster::bytes).isEqualTo(new byte[]{4, 5});
        verify(posterSource, never()).findPosterPath(any()); // path already known
    }

    @Test
    void returnsEmptyWhenTheImageDownloadFails() {
        when(repository.findByImdbId(TT)).thenReturn(Optional.of(TitlePoster.of(TT, "/p.jpg", NOW)));
        when(posterSource.download("/p.jpg", PosterSize.THUMB)).thenReturn(Optional.empty());

        assertThat(service().resolve(TT, PosterSize.THUMB)).isEmpty();
    }

    @Test
    void retriesTheReadOnceWhenAConcurrentInsertRacesTheDiscoveryRow() {
        final var service = service();
        // thumb() re-enters resolve() through the proxy; the first attempt loses the unique-key race.
        final var proxy = mock(PosterService.class);
        when(self.getObject()).thenReturn(proxy);
        final var poster = new PosterService.Poster(new byte[]{7}, "image/jpeg");
        when(proxy.resolve(TT, PosterSize.THUMB))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for key 'imdb_id'"))
                .thenReturn(Optional.of(poster));

        assertThat(service.thumb(TT)).contains(poster);
        verify(proxy, times(2)).resolve(TT, PosterSize.THUMB);
    }
}
