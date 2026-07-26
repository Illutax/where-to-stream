package tech.dobler.werstreamt.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TmdbPosterSourceTest {

    @Test
    void extractsTheMoviePosterPath() {
        final var json = """
                {"movie_results":[{"id":1,"poster_path":"/abc.jpg"}],"tv_results":[],"person_results":[]}""";
        assertThat(TmdbPosterSource.parsePosterPath(json)).contains("/abc.jpg");
    }

    @Test
    void fallsBackToTheTvPosterPathWhenThereIsNoMovie() {
        final var json = """
                {"movie_results":[],"tv_results":[{"poster_path":"/tv.jpg"}]}""";
        assertThat(TmdbPosterSource.parsePosterPath(json)).contains("/tv.jpg");
    }

    @Test
    void isEmptyWhenNoResultHasAPoster() {
        assertThat(TmdbPosterSource.parsePosterPath("""
                {"movie_results":[],"tv_results":[]}""")).isEmpty();
        assertThat(TmdbPosterSource.parsePosterPath("""
                {"movie_results":[{"id":1,"poster_path":null}]}""")).isEmpty();
    }

    @Test
    void isEmptyForMalformedJson() {
        assertThat(TmdbPosterSource.parsePosterPath("not json")).isEmpty();
    }
}
