package tech.dobler.where2stream.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tech.dobler.where2stream.application.InvalidImportException;
import tech.dobler.where2stream.application.NoSuchWatchlistEntryException;
import tech.dobler.where2stream.application.UserManagementException;
import tech.dobler.where2stream.application.ValidationException;
import tech.dobler.where2stream.application.WatchlistEntryAlreadyExistsException;
import tech.dobler.where2stream.domain.ScrapingException;

/**
 * Translates application exceptions into RFC-7807 {@link ProblemDetail} responses. Scoped to
 * the {@code api} package so it never intercepts the Thymeleaf controllers' redirect-based
 * error handling.
 */
@RestControllerAdvice(basePackages = "tech.dobler.where2stream.api")
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidImportException.class)
    public ProblemDetail handleInvalidImport(InvalidImportException ex) {
        final var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid import");
        return problem;
    }

    @ExceptionHandler(UserManagementException.class)
    public ProblemDetail handleUserManagement(UserManagementException ex) {
        return ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
    }

    @ExceptionHandler(NoSuchWatchlistEntryException.class)
    public ProblemDetail handleNoSuchWatchlistEntry(NoSuchWatchlistEntryException ex) {
        final var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Not on watchlist");
        return problem;
    }

    @ExceptionHandler(WatchlistEntryAlreadyExistsException.class)
    public ProblemDetail handleWatchlistEntryAlreadyExists(WatchlistEntryAlreadyExistsException ex) {
        final var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Already on watchlist");
        return problem;
    }

    @ExceptionHandler(ValidationException.class)
    public ProblemDetail handleValidation(ValidationException ex) {
        final var problem = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
        problem.setTitle("Invalid request");
        return problem;
    }

    @ExceptionHandler(ScrapingException.class)
    public ProblemDetail handleScraping(ScrapingException ex) {
        final var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        problem.setTitle("Upstream lookup failed");
        return problem;
    }
}

