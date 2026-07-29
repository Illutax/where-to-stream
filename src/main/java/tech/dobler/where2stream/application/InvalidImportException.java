package tech.dobler.where2stream.application;

/** Thrown when an uploaded watchlist CSV cannot be parsed into any usable entries (mapped to 400). */
public class InvalidImportException extends RuntimeException {
    public InvalidImportException(String message) {
        super(message);
    }
}
