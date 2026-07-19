package tech.dobler.werstreamt.application;

/**
 * Thrown when a caller asks to switch to a list that is not among the available asset files.
 * Carries the offending name so the web/REST layers can surface it (Thymeleaf redirect flash
 * attribute, or a 400 {@code ProblemDetail}).
 */
public class UnknownListException extends RuntimeException {
    private final String listName;

    public UnknownListException(String listName) {
        super("Unknown list: " + listName);
        this.listName = listName;
    }

    public String listName() {
        return listName;
    }
}
