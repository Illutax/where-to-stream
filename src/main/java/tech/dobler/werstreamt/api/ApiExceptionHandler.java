package tech.dobler.werstreamt.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tech.dobler.werstreamt.application.UnknownListException;

/**
 * Translates application exceptions into RFC-7807 {@link ProblemDetail} responses. Scoped to
 * the {@code api} package so it never intercepts the Thymeleaf controllers' redirect-based
 * error handling.
 */
@RestControllerAdvice(basePackages = "tech.dobler.werstreamt.api")
public class ApiExceptionHandler {

    @ExceptionHandler(UnknownListException.class)
    public ProblemDetail handleUnknownList(UnknownListException ex) {
        final var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Unknown list");
        problem.setProperty("listName", ex.listName());
        return problem;
    }
}
