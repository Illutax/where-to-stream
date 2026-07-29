package tech.dobler.where2stream.domain;

/**
 * Wraps an IO failure from an outbound scraping/HTTP call to a streaming-availability source
 * (mapped to 502 by {@code ApiExceptionHandler}). Lives in {@code domain} (not {@code services})
 * so the presentation-layer exception handler may depend on it without violating the layering
 * rules enforced by {@code ArchitectureTest} (TODO-20).
 */
public class ScrapingException extends RuntimeException {
    public ScrapingException(String message, Throwable cause) {
        super(message, cause);
    }
}
