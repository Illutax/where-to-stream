package tech.dobler.where2stream.titlecatalog.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import tech.dobler.where2stream.titlecatalog.application.PosterProperties;
import tech.dobler.where2stream.shared.domain.ImdbId;
import tech.dobler.where2stream.titlecatalog.domain.PosterSize;
import tech.dobler.where2stream.titlecatalog.domain.TitlePoster;
import tech.dobler.where2stream.titlecatalog.port.out.TitlePosterRepository;
import tech.dobler.where2stream.titlecatalog.port.out.PosterSource;
import tech.dobler.where2stream.shared.time.TimeService;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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

    // self.getObject() returns the service itself, so the (proxied-in-prod) read/write steps run
    // directly against the mocked repository in the test.
    private PosterService service() {
        lenient().when(timeService.now()).thenReturn(NOW);
        lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        final var svc = new PosterService(repository, posterSource, new PosterProperties(14), timeService, self);
        lenient().when(self.getObject()).thenReturn(svc);
        return svc;
    }

    @Test
    void servesTheCachedThumbnailWithoutHittingTheSource() {
        final var row = TitlePoster.of(TT, "/p.jpg", NOW);
        row.setThumb(new byte[]{1, 2, 3}, "image/jpeg");
        when(repository.findByImdbId(TT)).thenReturn(Optional.of(row));

        final var result = service().thumb(TT);

        assertThat(result).get().extracting(PosterService.Poster::bytes).isEqualTo(new byte[]{1, 2, 3});
        verifyNoInteractions(posterSource);
    }

    @Test
    void onMissDiscoversThePathDownloadsAndCaches() {
        when(repository.findByImdbId(TT)).thenReturn(Optional.empty());
        when(posterSource.findPosterPath(TT)).thenReturn(Optional.of("/p.jpg"));
        when(posterSource.download("/p.jpg", PosterSize.THUMB)).thenReturn(Optional.of(new byte[]{9, 8, 7}));

        final var result = service().thumb(TT);

        assertThat(result).get().extracting(PosterService.Poster::bytes).isEqualTo(new byte[]{9, 8, 7});
        verify(posterSource).findPosterPath(TT);
        verify(posterSource).download("/p.jpg", PosterSize.THUMB);
    }

    @Test
    void honoursTheNegativeCacheWithoutAskingTheSourceAgain() {
        when(repository.findByImdbId(TT)).thenReturn(Optional.of(TitlePoster.of(TT, null, NOW))); // fresh negative

        assertThat(service().thumb(TT)).isEmpty();
        verify(posterSource, never()).findPosterPath(any());
    }

    @Test
    void fetchesTheFullImageOnDemandWhenOnlyTheThumbIsCached() {
        final var row = TitlePoster.of(TT, "/p.jpg", NOW);
        row.setThumb(new byte[]{1}, "image/jpeg");
        when(repository.findByImdbId(TT)).thenReturn(Optional.of(row));
        when(posterSource.download("/p.jpg", PosterSize.FULL)).thenReturn(Optional.of(new byte[]{4, 5}));

        final var result = service().full(TT);

        assertThat(result).get().extracting(PosterService.Poster::bytes).isEqualTo(new byte[]{4, 5});
        verify(posterSource, never()).findPosterPath(any()); // path already known
    }

    @Test
    void returnsEmptyWhenTheImageDownloadFails() {
        when(repository.findByImdbId(TT)).thenReturn(Optional.of(TitlePoster.of(TT, "/p.jpg", NOW)));
        when(posterSource.download("/p.jpg", PosterSize.THUMB)).thenReturn(Optional.empty());

        assertThat(service().thumb(TT)).isEmpty();
    }

    @Test
    void returnsEmptyWhenTheImageDownloadYieldsEmptyBytes() {
        // A distinct path from an absent Optional: the source responds but with a zero-length body.
        when(repository.findByImdbId(TT)).thenReturn(Optional.of(TitlePoster.of(TT, "/p.jpg", NOW)));
        when(posterSource.download("/p.jpg", PosterSize.THUMB)).thenReturn(Optional.of(new byte[0]));

        assertThat(service().thumb(TT)).isEmpty();
    }

    @Test
    void discoveryFindingNoPosterPathAtAllReturnsEmpty() {
        when(repository.findByImdbId(TT)).thenReturn(Optional.empty());
        when(posterSource.findPosterPath(TT)).thenReturn(Optional.empty());

        assertThat(service().thumb(TT)).isEmpty();
        verify(posterSource, never()).download(any(), any());
    }

    @Test
    void cachedEmptyBytesAreTreatedAsNeedingARedownload() {
        // A previously-stored zero-length blob (e.g. an interrupted write) must not be served as a hit.
        final var row = TitlePoster.of(TT, "/p.jpg", NOW);
        row.setThumb(new byte[0], "image/jpeg");
        when(repository.findByImdbId(TT)).thenReturn(Optional.of(row));
        when(posterSource.download("/p.jpg", PosterSize.THUMB)).thenReturn(Optional.of(new byte[]{1}));

        final var result = service().thumb(TT);

        assertThat(result).get().extracting(PosterService.Poster::bytes).isEqualTo(new byte[]{1});
        verify(posterSource, never()).findPosterPath(any()); // path already known, only bytes were missing
    }

    @Test
    void staleNegativeCacheAsksTheSourceAgainAndRefreshesTheExistingRow() {
        final var stalePast = NOW.minus(30, java.time.temporal.ChronoUnit.DAYS); // past the 14-day TTL
        final var row = TitlePoster.of(TT, null, stalePast);
        when(repository.findByImdbId(TT)).thenReturn(Optional.of(row));
        when(posterSource.findPosterPath(TT)).thenReturn(Optional.of("/new.jpg"));
        when(posterSource.download("/new.jpg", PosterSize.THUMB)).thenReturn(Optional.of(new byte[]{1}));

        final var result = service().thumb(TT);

        assertThat(result).isPresent();
        verify(posterSource).findPosterPath(TT);
        assertThat(row.getPosterPath()).isEqualTo("/new.jpg"); // refreshed in place, not replaced
    }

    @Test
    void fullSizeCacheHitUsesTheStoredFullContentType() {
        final var row = TitlePoster.of(TT, "/p.jpg", NOW);
        row.setThumb(new byte[]{1}, "image/jpeg");
        row.setFull(new byte[]{2, 3}, "image/png");
        when(repository.findByImdbId(TT)).thenReturn(Optional.of(row));

        final var result = service().full(TT);

        assertThat(result).get().extracting(PosterService.Poster::contentType).isEqualTo("image/png");
        verifyNoInteractions(posterSource);
    }

    @Test
    void contentTypeFallsBackToJpegWhenNoneWasStored() {
        final var row = TitlePoster.of(TT, "/p.jpg", NOW);
        row.setThumb(new byte[]{1}, null);
        when(repository.findByImdbId(TT)).thenReturn(Optional.of(row));

        final var result = service().thumb(TT);

        assertThat(result).get().extracting(PosterService.Poster::contentType).isEqualTo("image/jpeg");
    }

    @Test
    void swallowsAConcurrentInsertAndStillServesTheImage() {
        // Cold miss: the discovery/store insert loses the unique-key race, but the request still
        // returns the bytes it downloaded (the row self-heals on a later request).
        final var service = service();
        when(repository.findByImdbId(TT)).thenReturn(Optional.empty());
        when(posterSource.findPosterPath(TT)).thenReturn(Optional.of("/p.jpg"));
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("Duplicate entry for key 'imdb_id'"));
        when(posterSource.download("/p.jpg", PosterSize.THUMB)).thenReturn(Optional.of(new byte[]{7}));

        assertThat(service.thumb(TT)).get().extracting(PosterService.Poster::bytes).isEqualTo(new byte[]{7});
        verify(posterSource).download("/p.jpg", PosterSize.THUMB);
    }
}
