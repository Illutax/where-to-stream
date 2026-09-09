package tech.dobler.where2stream.watchlist.application;

import org.junit.jupiter.api.Test;
import tech.dobler.where2stream.watchlist.domain.ImdbEntry;
import tech.dobler.where2stream.watchlist.domain.InvalidImportException;
import tech.dobler.where2stream.shared.kernel.domain.ImdbId;
import tech.dobler.where2stream.shared.kernel.domain.ReleaseYear;
import tech.dobler.where2stream.watchlist.domain.WatchlistDate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class ExportReaderTest {

    private final ExportReader exportReader = new ExportReader();

    /** The sample IMDb export, opened as a classpath resource (uploads arrive as a stream). */
    private static InputStream sampleExport() {
        final var in = ExportReaderTest.class.getResourceAsStream("/test-assets/2024-12-25_Test.csv");
        assertThat(in).as("sample export fixture present").isNotNull();
        return in;
    }

    /** The entries only; the unreadable-row count has its own tests. */
    private List<ImdbEntry> parse(InputStream csv) {
        return exportReader.parse(csv).entries();
    }

    private static InputStream streamOf(String csv) {
        return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesAllRows() {
        final List<ImdbEntry> entries = parse(sampleExport());

        assertThat(entries).hasSize(35);
    }

    @Test
    void mapsColumnsOfFirstEntry() {
        final ImdbEntry first = parse(sampleExport()).getFirst();

        final var expected = List.of(
                "The Prestige",
                ImdbId.of("tt0482571"),
                URI.create("https://www.imdb.com/title/tt0482571/"),
                WatchlistDate.of("2012-06-22"),
                ReleaseYear.of(2006),
                true);
        assertThat(first)
                .extracting(
                        ImdbEntry::name,
                        ImdbEntry::imdbId,
                        ImdbEntry::url,
                        ImdbEntry::added,
                        ImdbEntry::year,
                        ImdbEntry::isRated)
                .isEqualTo(expected);
    }

    @Test
    void extractsImdbIdFromUrl() {
        final List<ImdbEntry> entries = parse(sampleExport());

        assertThat(entries)
                .extracting(ImdbEntry::imdbId)
                .allMatch(id -> id.value().startsWith("tt"));
    }

    @Test
    void handlesQuotedTitlesWithApostrophes() {
        final List<ImdbEntry> entries = parse(sampleExport());

        assertThat(entries)
                .extracting(ImdbEntry::name)
                .contains("Schindler's List", "Ocean's Eleven", "Kill Bill: Vol. 1");
    }

    @Test
    void marksEntriesWithYourRatingAsRated() {
        final List<ImdbEntry> entries = parse(sampleExport());

        // Every row in the fixture has a "Your Rating" value, so all entries are rated.
        assertThat(entries).allMatch(ImdbEntry::isRated);
    }

    @Test
    void storesTheCanonicalImdbUrlNotTheRawCsvValue() {
        // The raw URL carries a javascript: payload but still contains a valid IMDb id substring.
        // The imdbId is extracted and the stored URL is rebuilt from it — the payload is dropped.
        final var csv = """
                Position,Const,Created,Modified,Description,Title,Original Title,URL,Title Type,IMDb Rating,Runtime (mins),Year,Genres,Num Votes,Release Date,Directors,Your Rating,Date Rated
                1,tt0000001,2012-06-22,2012-06-22,,"Evil","Evil",javascript:alert(1)//https://www.imdb.com/title/tt1337/,Movie,8.5,130,2006,"Drama",1,2006-10-20,"Dir",10,2012-06-22
                """;
        final var in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        final ImdbEntry entry = parse(in).getFirst();

        assertThat(entry).extracting(ImdbEntry::imdbId, ImdbEntry::url)
                .containsExactly(ImdbId.of("tt1337"), URI.create("https://www.imdb.com/title/tt1337/"));
    }

    @Test
    void mapsByColumnNameSoAnInsertedOrReorderedColumnIsHarmless() {
        // The case TODO-22 was about, and the one the old positional mapping got silently wrong:
        // columns shuffled and a new one inserted. Read by position this yields a valid-looking
        // entry built from the wrong fields; read by name it is simply correct.
        final var csv = """
                Const,Streaming Provider,Title,URL,Year,Created,Your Rating
                tt0000001,Netflix,"The Prestige",https://www.imdb.com/title/tt0000001/,2006,2012-06-22,10
                """;

        final ImdbEntry entry = parse(streamOf(csv)).getFirst();

        assertThat(entry)
                .extracting(ImdbEntry::name, ImdbEntry::imdbId, ImdbEntry::year, ImdbEntry::added, ImdbEntry::isRated)
                .containsExactly("The Prestige", ImdbId.of("tt0000001"), ReleaseYear.of(2006),
                        WatchlistDate.of("2012-06-22"), true);
    }

    @Test
    void rejectsAFileMissingAColumnWeRead() {
        // "Title" renamed — every other column intact, and every row still carries a valid tt… link.
        // This is precisely the file that used to import as garbage and then delete the difference.
        final var csv = """
                Position,Const,Created,Primary Title,URL,Year,Your Rating
                1,tt0000001,2012-06-22,"The Prestige",https://www.imdb.com/title/tt0000001/,2006,10
                """;

        assertThatThrownBy(() -> parse(streamOf(csv)))
                .isInstanceOf(InvalidImportException.class)
                .hasMessageContaining("missing the column(s) Title")
                .hasMessageContaining("your watchlist is unchanged")
                .hasMessageContaining("Primary Title");
    }

    @Test
    void rejectsADuplicateColumnNameRatherThanPickingOne() {
        final var csv = """
                Created,Title,Title,URL,Year,Your Rating
                2012-06-22,"Real","Decoy",https://www.imdb.com/title/tt0000001/,2006,10
                """;

        assertThatThrownBy(() -> parse(streamOf(csv)))
                .isInstanceOf(InvalidImportException.class)
                .hasMessageContaining("unusable header row");
    }

    @Test
    void readsAFileThatStartsWithAByteOrderMark() {
        // A BOM would otherwise be glued onto the first column's name. Harmless today (the first
        // column is one we do not read), which is exactly why it would go unnoticed until it wasn't.
        final var csv = "\uFEFF" + """
                Created,Title,URL,Year,Your Rating
                2012-06-22,"The Prestige",https://www.imdb.com/title/tt0000001/,2006,10
                """;

        assertThat(parse(streamOf(csv)))
                .extracting(ImdbEntry::name)
                .containsExactly("The Prestige");
    }

    @Test
    void countsTheRowsItCouldNotRead() {
        // The count is what stops the import deleting (TODO-70), so it is asserted rather than
        // left to be inferred from the entry count — a row can also vanish through de-duplication.
        final var csv = """
                Created,Title,URL,Year,Your Rating
                2012-06-22,"Good One",https://www.imdb.com/title/tt0000001/,2006,10
                2012-06-22,"Bad Year",https://www.imdb.com/title/tt0000002/,notayear,10
                2012-06-22,"Bad Url",not-an-imdb-url,2010,10
                2012-06-22,"Good Two",https://www.imdb.com/title/tt0000004/,2011,10
                """;

        final var parsed = exportReader.parse(streamOf(csv));

        assertThat(parsed)
                .extracting(p -> p.entries().size(), ExportReader.ParsedExport::unreadableRows,
                        ExportReader.ParsedExport::isComplete)
                .containsExactly(2, 2, false);
    }

    @Test
    void reportsAFullyReadableFileAsComplete() {
        final var parsed = exportReader.parse(sampleExport());

        assertThat(parsed)
                .extracting(ExportReader.ParsedExport::unreadableRows, ExportReader.ParsedExport::isComplete)
                .containsExactly(0, true);
    }

    @Test
    void skipsMalformedRows() {
        final var csv = """
                Position,Const,Created,Modified,Description,Title,Original Title,URL,Title Type,IMDb Rating,Runtime (mins),Year,Genres,Num Votes,Release Date,Directors,Your Rating,Date Rated
                1,tt0000001,2012-06-22,2012-06-22,,"Good One","Good One",https://www.imdb.com/title/tt0000001/,Movie,8.5,130,2006,"Drama",1,2006-10-20,"Dir",10,2012-06-22
                2,tt0000002,2012-06-22,2012-06-22,,"Bad Year","Bad Year",https://www.imdb.com/title/tt0000002/,Movie,8.5,130,notayear,"Drama",1,2006-10-20,"Dir",10,2012-06-22
                3,tt0000003,2012-06-22,2012-06-22,,"Bad Url","Bad Url",not-an-imdb-url,Movie,8.5,130,2010,"Drama",1,2006-10-20,"Dir",10,2012-06-22
                4,tt0000004,2012-06-22,2012-06-22,,"Good Two","Good Two",https://www.imdb.com/title/tt0000004/,Movie,8.5,130,2011,"Drama",1,2006-10-20,"Dir",10,2012-06-22
                """;
        final var in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        final List<ImdbEntry> entries = parse(in);

        // Bad-year and bad-url rows are skipped; the two well-formed rows survive.
        assertThat(entries)
                .extracting(ImdbEntry::name, ImdbEntry::imdbId)
                .containsExactly(
                        tuple("Good One", ImdbId.of("tt0000001")),
                        tuple("Good Two", ImdbId.of("tt0000004")));
    }
}
