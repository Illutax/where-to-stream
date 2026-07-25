package tech.dobler.werstreamt.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import tech.dobler.werstreamt.domain.ImdbEntry;

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
        final var created = record.get("Created");
        final var name = record.get("Title");
        final var url = record.get("URL");
        final var yearString = record.get("Year");
        final var year = yearString.isBlank() ? 0 : Integer.parseInt(yearString);
        final var isRated = !record.get("Your Rating").isBlank();
        final var imdbId = extractImdbId(url);
        return new ImdbEntry(name, URI.create(url), created, isRated, year, imdbId);
    }

    private static String extractImdbId(String url) {
        final var matcher = PATTERN.matcher(url);
        if (!matcher.find()) throw new IllegalArgumentException("Couldn't find imdbId for url %s".formatted(url));
        return matcher.group(1);
    }
}
