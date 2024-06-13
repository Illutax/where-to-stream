package tech.dobler.werstreamt.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tech.dobler.werstreamt.entities.ImdbEntry;

import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    @Value("${wer-streamt.path:d3918245-a9b8-48ea-99fb-cce13ef4bee3}")
    private String fileName;

    private final static Pattern PATTERN = Pattern.compile("https://www.imdb.com/title/(tt\\w+)/");

    public List<ImdbEntry> parse() {
        final var entries = new ArrayList<ImdbEntry>();
        AtomicInteger counter = new AtomicInteger(0);
        try (var reader = makeReader(fileName)) {
            final var records = reader.getRecords();
            for (var record : records) {
                final var created = record.get("Created");
                final var name = record.get("Title");
                final var url = record.get("URL");
                final var isRated = !record.get("Your Rating").isBlank();
                final var id = extractImdbId(url);
                final var entry = new ImdbEntry(counter.incrementAndGet(), name, URI.create(url), created, isRated, id);
                entries.add(entry);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        log.debug(entries.stream()
                .map(ImdbEntry::toString)
                .collect(Collectors.joining(System.lineSeparator())));
        return entries;
    }

    private static String extractImdbId(String url) {
        final var matcher = PATTERN.matcher(url);
        if (!matcher.find()) throw new IllegalArgumentException("Couldn't find imdbId for url %s".formatted(url));
        return matcher.group(1);
    }

    private static CSVParser makeReader(String filePath) throws IOException {
        final var csvFilePath = Paths.get("assets", filePath + ".csv");
        log.info("Reading csv from path: {}", csvFilePath.toAbsolutePath());

        final var fileReader = new FileReader(csvFilePath.toFile());
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader(headers)
                .setSkipHeaderRecord(true)
                .build();

        return csvFormat.parse(fileReader);
    }
}
