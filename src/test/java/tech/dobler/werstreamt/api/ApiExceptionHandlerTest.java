package tech.dobler.werstreamt.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tech.dobler.werstreamt.application.ValidationException;
import tech.dobler.werstreamt.domain.ScrapingException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void validationExceptionDefaultsToBadRequest() {
        final var problem = handler.handleValidation(new ValidationException("A theme is required."));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).isEqualTo("A theme is required.");
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

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
        assertThat(problem.getDetail()).isEqualTo("Query for imdbId 'tt1' failed");
    }
}
