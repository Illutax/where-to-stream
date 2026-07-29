package tech.dobler.where2stream.shared.platform.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import tech.dobler.where2stream.shared.platform.api.ValidationException;
import tech.dobler.where2stream.streamingavailability.domain.ScrapingException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void validationExceptionDefaultsToBadRequest() {
        final var problem = handler.handleValidation(new ValidationException("A theme is required."));

        assertThat(problem).extracting(ProblemDetail::getStatus, ProblemDetail::getDetail)
                .containsExactly(HttpStatus.BAD_REQUEST.value(), "A theme is required.");
    }

    @Test
    void validationExceptionHonoursAnExplicitStatus() {
        final var problem = handler.handleValidation(new ValidationException(HttpStatus.CONFLICT, "Already taken."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void scrapingExceptionMapsToBadGateway() {
        final var cause = new java.io.IOException("connection reset");
        final var problem = handler.handleScraping(new ScrapingException("Query for imdbId 'tt1' failed", cause));

        assertThat(problem).extracting(ProblemDetail::getStatus, ProblemDetail::getDetail)
                .containsExactly(HttpStatus.BAD_GATEWAY.value(), "Query for imdbId 'tt1' failed");
    }
}
