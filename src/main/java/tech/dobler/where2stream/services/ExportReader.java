package tech.dobler.where2stream.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import tech.dobler.where2stream.domain.ImdbEntry;
import tech.dobler.where2stream.domain.ImdbId;
import tech.dobler.where2stream.domain.ReleaseYear;
import tech.dobler.where2stream.domain.WatchlistDate;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parses an IMDb CSV watchlist export (as uploaded by a user) into {@link ImdbEntry} records.
 * Malformed rows are skipped and logged.
 */
@Slf4j
@Service
public class ExportReader {
    private static final String[] headers = new String[]{
            "Position",
            "Const",
            "Created",
            "Modified",
            "Description",
            "Title",
            "Original Title",
            "URL",
            "Title Type",
            "IMDb Rating",
            "Runtime (mins)",
            "Year",
            "Genres",
            "Num Votes",
            "Release Date",
            "Directors",
            "Your Rating",
            "Date Rated",
    };

    private final static Pattern PATTERN = Pattern.compile("https://www.imdb.com/title/(tt\\w+)/");

    /** Parses the given IMDb CSV export stream (UTF-8). The stream is not closed by this method. */
    public List<ImdbEntry> parse(InputStream csv) {
        final var entries = new ArrayList<ImdbEntry>();
        final CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader(headers)
                .setSkipHeaderRecord(true)
                .build();
        try (var reader = new InputStreamReader(csv, StandardCharsets.UTF_8);
             CSVParser parser = csvFormat.parse(reader)) {
            for (var record : parser.getRecords()) {
                try {
                    entries.add(toEntry(record));
                } catch (RuntimeException e) {
                    log.warn("Skipping malformed CSV row {}: {}", record.getRecordNumber(), e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        log.debug("Parsed {} watchlist entries", entries.size());
        return entries;
    }

    private ImdbEntry toEntry(CSVRecord record) {
        final var created = WatchlistDate.of(record.get("Created"));
        final var name = record.get("Title");
        final var yearString = record.get("Year");
        final var year = ReleaseYear.of(yearString.isBlank() ? 0 : Integer.parseInt(yearString));
        final var isRated = !record.get("Your Rating").isBlank();
        final var imdbId = extractImdbId(record.get("URL"));
        // Build the stored URL from the validated imdbId (tt\w+) rather than keeping the raw CSV
        // field: the raw value is attacker-controlled and unused by the API/UI, so canonicalising
        // it here removes any chance of persisting a non-IMDb payload (e.g. a javascript: URL).
        return new ImdbEntry(name, canonicalUrl(imdbId), created, isRated, year, imdbId);
    }

    private static ImdbId extractImdbId(String url) {
        final var matcher = PATTERN.matcher(url);
        if (!matcher.find()) throw new IllegalArgumentException("Couldn't find imdbId for url %s".formatted(url));
        return ImdbId.of(matcher.group(1));
    }

    private static URI canonicalUrl(ImdbId imdbId) {
        return URI.create("https://www.imdb.com/title/" + imdbId.value() + "/");
    }
}
