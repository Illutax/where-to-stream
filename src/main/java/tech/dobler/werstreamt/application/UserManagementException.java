package tech.dobler.werstreamt.application;

import org.springframework.http.HttpStatus;

/**
 * A user-administration error carrying the HTTP status the API should return (mapped to a
 * {@code ProblemDetail} by {@code ApiExceptionHandler}).
 */
public class UserManagementException extends RuntimeException {

    private final HttpStatus status;

    public UserManagementException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static UserManagementException notFound(String id) {
        return new UserManagementException(HttpStatus.NOT_FOUND, "No user with id " + id);
    }

    public static UserManagementException duplicateUsername(String username) {
        return new UserManagementException(HttpStatus.CONFLICT, "Username already taken: " + username);
    }

    public static UserManagementException badRequest(String message) {
        return new UserManagementException(HttpStatus.BAD_REQUEST, message);
    }

    public static UserManagementException lastAdmin() {
        return new UserManagementException(HttpStatus.CONFLICT,
                "Refusing to remove the last enabled admin — the system would lock itself out.");
    }
}
