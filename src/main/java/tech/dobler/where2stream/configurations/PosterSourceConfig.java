package tech.dobler.where2stream.configurations;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tech.dobler.where2stream.services.ImdbPosterSource;
import tech.dobler.where2stream.services.PosterSource;
import tech.dobler.where2stream.services.TmdbPosterSource;

/**
 * Selects the active {@link PosterSource} at startup: TMDB when it is enabled and configured (see
 * {@link TmdbProperties#active()}), otherwise IMDb (the default). A misconfigured TMDB (flag on, key
 * missing) falls back to IMDb so posters keep working.
 */
@Slf4j
@Configuration
public class PosterSourceConfig {

    @Bean
    @Primary
    public PosterSource posterSource(TmdbProperties tmdb, TmdbPosterSource tmdbPosterSource,
                                     ImdbPosterSource imdbPosterSource) {
        if (tmdb.active()) {
            log.info("Poster source: TMDB");
            return tmdbPosterSource;
        }
        log.info("Poster source: IMDb (GraphQL API)");
        return imdbPosterSource;
    }
}
