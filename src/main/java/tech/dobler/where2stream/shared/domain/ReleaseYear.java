package tech.dobler.where2stream.shared.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A title's release year.
 * A value object over the former bare {@code int}: it owns the domain rule that year {@code 0}
 * marks a title with no release year yet ("Not yet released").
 * Serialises to/from a plain JSON number ({@link JsonValue} / {@link JsonCreator}) so the
 * {@code /api} contract for the numeric year is unchanged.
 */
public record ReleaseYear(int value) {

    /** Rendered for a title that has no release year yet (year {@code 0}). */
    public static final String NOT_YET_RELEASED = "Not yet released";

    public ReleaseYear {
        if (value < 0) {
            throw new IllegalArgumentException("Invalid release year: " + value);
        }
    }

    @JsonCreator
    public static ReleaseYear of(int value) {
        return new ReleaseYear(value);
    }

    public boolean isReleased() {
        return value > 0;
    }

    /** Human-readable year, or the "Not yet released" placeholder when unreleased. */
    public String display() {
        return isReleased() ? String.valueOf(value) : NOT_YET_RELEASED;
    }

    @JsonValue
    @Override
    public int value() {
        return value;
    }
}
