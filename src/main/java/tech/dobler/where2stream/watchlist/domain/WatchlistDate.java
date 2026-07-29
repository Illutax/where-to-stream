package tech.dobler.where2stream.watchlist.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.NonNull;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * The date a title was added to a watchlist (the IMDb export "Created" column). A value object over
 * the former bare {@code String}: it types the value as a watchlist date and offers
 * {@link #toLocalDate()} for real date semantics. The raw value is kept and exposed
 * ({@link JsonValue}) — for IMDb exports it is ISO-8601 ({@code yyyy-MM-dd}), so it sorts
 * chronologically as text and the JSON/DB contracts stay unchanged. Parsing is intentionally lazy
 * (in {@code toLocalDate}) so a non-ISO value never drops a row on import.
 */
public record WatchlistDate(String value) implements Comparable<WatchlistDate> {

    public WatchlistDate {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Blank watchlist date");
        }
    }

    @JsonCreator
    public static WatchlistDate of(String value) {
        return new WatchlistDate(value);
    }

    /** The date if the value is ISO-8601, otherwise empty (the raw string is always available). */
    public Optional<LocalDate> toLocalDate() {
        try {
            return Optional.of(LocalDate.parse(value));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    @JsonValue
    @Override
    public String value() {
        return value;
    }

    @Override
    public int compareTo(@NonNull WatchlistDate other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
