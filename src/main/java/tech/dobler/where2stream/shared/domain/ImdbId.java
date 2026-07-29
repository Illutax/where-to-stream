package tech.dobler.where2stream.shared.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.regex.Pattern;

/**
 * An IMDb title id (e.g. {@code tt0482571}).
 * A value object over the former bare {@code String}: construction validates the {@code tt\w+}
 * format in one place, so an {@code ImdbId} in hand is always well-formed
 * and can't be confused with an arbitrary string.
 *
 * <p>Serialises to/from a plain JSON string ({@link JsonValue} / {@link JsonCreator}) and maps to a
 * {@code varchar} column via {@code ImdbIdConverter}, so the JSON and DB contracts are unchanged.
 */
public record ImdbId(String value) {

    private static final Pattern VALID = Pattern.compile("tt\\w+");

    public ImdbId {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid IMDb id: " + value);
        }
    }

    @JsonCreator
    public static ImdbId of(String value) {
        return new ImdbId(value);
    }

    @JsonValue
    @Override
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
