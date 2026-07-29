package tech.dobler.where2stream.application;

import org.springframework.http.HttpStatus;

/**
 * A request-validation error (missing/malformed field, conflicting value), carrying the HTTP
 * status the API should return (mapped to a {@code ProblemDetail} by {@code ApiExceptionHandler}).
 * Replaces the raw {@code ResponseStatusException} throws that used to bypass it (F12).
 */
public class ValidationException extends RuntimeException {

    private final HttpStatus status;

    public ValidationException(String message) {
        this(HttpStatus.BAD_REQUEST, message);
    }

    public ValidationException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
