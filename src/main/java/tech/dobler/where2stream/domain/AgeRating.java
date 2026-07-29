package tech.dobler.where2stream.domain;

/**
 * A title's age rating. {@link RatingSystem#FSK} carries the German FSK label ({@code 0/6/12/16/18},
 * shown in the FSK colour scheme); {@link RatingSystem#OTHER} carries a foreign primary certificate
 * (e.g. the US {@code R}/{@code PG-13}) used as a fallback when no German rating exists.
 */
public record AgeRating(RatingSystem system, String label) {

    public AgeRating {
        if (system == null || label == null || label.isBlank()) {
            throw new IllegalArgumentException("An age rating needs a system and a non-blank label");
        }
    }

    public static AgeRating fsk(String label) {
        return new AgeRating(RatingSystem.FSK, label);
    }

    public static AgeRating other(String label) {
        return new AgeRating(RatingSystem.OTHER, label);
    }

    public enum RatingSystem {
        FSK,
        OTHER
    }
}
