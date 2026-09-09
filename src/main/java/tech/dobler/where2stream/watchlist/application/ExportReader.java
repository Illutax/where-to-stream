package tech.dobler.where2stream.watchlist.application;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.DuplicateHeaderMode;
import org.springframework.stereotype.Service;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.shared.kernel.domain.ReleaseYear;
import tech.dobler.where2stream.watchlist.domain.ImdbEntry;
import tech.dobler.where2stream.watchlist.domain.InvalidImportException;
import tech.dobler.where2stream.watchlist.domain.WatchlistDate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parses an IMDb CSV watchlist export (as uploaded by a user) into {@link ImdbEntry} records.
 * Malformed rows are skipped and logged; a file whose header does not carry the columns we read is
 * rejected outright.
 *
 * <p><strong>The columns are matched by name, and that is a safety property rather than a
 * convenience.</strong> This reader used to declare all 18 IMDb column names itself and discard the
 * file's real header row, so the mapping was purely positional (TODO-22). A wholly foreign file
 * failed loudly — every row fell through and the import reported nothing usable. The dangerous case
 * was in between: IMDb inserting <em>one</em> column, or reordering them. Then {@code get("Title")}
 * quietly returned a different field, rows still carried a valid {@code tt…} link, and they were
 * imported as garbage. Because the import is a <em>full sync</em>, whatever the misread file
 * appeared not to contain was deleted from the user's watchlist.
 *
 * <p>So the rule is: read the header the file actually has, check the columns we depend on are in
 * it, and refuse the file if they are not. Refusing happens here, before
 * {@code WatchlistImportService} has looked at a single stored row, which is what keeps a schema
 * change from turning into a deletion.
 *
 * <p>Only the five columns actually read are required. IMDb is free to add, drop or move anything
 * else — the point of matching by name is that it costs us nothing.
 */
@Slf4j
@Service
public class ExportReader {

    /** The columns {@link #toEntry} reads. Everything else in the export is ignored. */
    private static final List<String> REQUIRED_COLUMNS = List.of("Created", "Title", "Year", "Your Rating", "URL");

    private static final char BYTE_ORDER_MARK = '\uFEFF';

    private static final Pattern PATTERN = Pattern.compile("https://www.imdb.com/title/(tt\\w+)/");

    /**
     * Header taken from the file itself.
     *
     * <p>{@code DISALLOW} matters: with duplicates permitted, two columns called {@code Title}
     * would make {@code get("Title")} pick one of them silently — the same class of quiet
     * wrong-field read this change exists to remove. Better a rejected file than a plausible one.
     */
    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW)
            .build();

    /** Parses the given IMDb CSV export stream (UTF-8). The stream is not closed by this method. */
    public List<ImdbEntry> parse(InputStream csv) {
        final var entries = new ArrayList<ImdbEntry>();
        try (var reader = withoutByteOrderMark(csv);
             CSVParser parser = FORMAT.parse(reader)) {
            requireKnownColumns(parser.getHeaderNames());
            for (var record : parser.getRecords()) {
                try {
                    entries.add(toEntry(record));
                } catch (RuntimeException e) {
                    log.warn("Skipping malformed CSV row {}: {}", record.getRecordNumber(), e.getMessage());
                }
            }
        } catch (IllegalArgumentException e) {
            // commons-csv rejects a header it cannot index by — duplicate or empty column names.
            // Unhandled this would surface as a 500; it is a bad upload, not a bug.
            throw new InvalidImportException("The uploaded file has an unusable header row: " + e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        log.debug("Parsed {} watchlist entries", entries.size());
        return entries;
    }

    private static void requireKnownColumns(List<String> header) {
        final var missing = REQUIRED_COLUMNS.stream().filter(column -> !header.contains(column)).toList();
        if (missing.isEmpty()) {
            return;
        }
        throw new InvalidImportException(
                "The uploaded file is missing the column(s) %s, so nothing was imported and your watchlist is unchanged. "
                        .formatted(String.join(", ", missing))
                        + "Expected an IMDb watchlist export containing %s; this file has %s."
                        .formatted(String.join(", ", REQUIRED_COLUMNS),
                                header.isEmpty() ? "no header row" : String.join(", ", header)));
    }

    /**
     * A UTF-8 BOM would otherwise become part of the first column's name.
     *
     * <p>It did not matter while the header was supplied by this class; now that the file's own
     * header is what the lookup keys on, a leading {@code U+FEFF} would rename whichever column
     * comes first. Today that is {@code Position}, which we do not read — so this guards against
     * the day IMDb reorders, not against anything currently observed.
     */
    private static Reader withoutByteOrderMark(InputStream csv) throws IOException {
        final var reader = new BufferedReader(new InputStreamReader(csv, StandardCharsets.UTF_8));
        reader.mark(1);
        if (reader.read() != BYTE_ORDER_MARK) {
            reader.reset();
        }
        return reader;
    }

    private ImdbEntry toEntry(CSVRecord record) {
        final var created = WatchlistDate.of(record.get("Created"));
        final var name = record.get("Title");
        final var yearString = record.get("Year");
        final var year = ReleaseYear.of(yearString.isBlank() ? 0 : Integer.parseInt(yearString));
        final var isRated = !record.get("Your Rating").isBlank();
        final var imdbId = extractImdbId(record.get("URL"));
        // Build the stored URL from the validated imdbId (tt\w+) rather than keeping the raw CSV field:
        // the raw value is attacker-controlled and unused by the API/UI,
        // so canonicalising it here removes any chance of persisting a non-IMDb payload (e.g. a javascript: URL).
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
