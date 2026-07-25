package tech.dobler.werstreamt.services;

import org.junit.jupiter.api.Test;
import tech.dobler.werstreamt.domain.ImdbEntry;
import tech.dobler.werstreamt.domain.ImdbId;
import tech.dobler.werstreamt.domain.ReleaseYear;
import tech.dobler.werstreamt.domain.WatchlistDate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ExportReaderTest {

    private final ExportReader exportReader = new ExportReader();

    /** The sample IMDb export, opened as a classpath resource (uploads arrive as a stream). */
    private static InputStream sampleExport() {
        final var in = ExportReaderTest.class.getResourceAsStream("/test-assets/2024-12-25_Test.csv");
        assertThat(in).as("sample export fixture present").isNotNull();
        return in;
    }

    @Test
    void parsesAllRows() {
        final List<ImdbEntry> entries = exportReader.parse(sampleExport());

        assertThat(entries).hasSize(35);
    }

    @Test
    void mapsColumnsOfFirstEntry() {
        final ImdbEntry first = exportReader.parse(sampleExport()).getFirst();

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
        final List<ImdbEntry> entries = exportReader.parse(sampleExport());

        assertThat(entries)
                .extracting(ImdbEntry::imdbId)
                .allMatch(id -> id.value().startsWith("tt"));
    }

    @Test
    void handlesQuotedTitlesWithApostrophes() {
        final List<ImdbEntry> entries = exportReader.parse(sampleExport());

        assertThat(entries)
                .extracting(ImdbEntry::name)
                .contains("Schindler's List", "Ocean's Eleven", "Kill Bill: Vol. 1");
    }

    @Test
    void marksEntriesWithYourRatingAsRated() {
        final List<ImdbEntry> entries = exportReader.parse(sampleExport());

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

        final ImdbEntry entry = exportReader.parse(in).getFirst();

        assertThat(entry.imdbId()).isEqualTo(ImdbId.of("tt1337"));
        assertThat(entry.url()).isEqualTo(URI.create("https://www.imdb.com/title/tt1337/"));
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

        final List<ImdbEntry> entries = exportReader.parse(in);

        // Bad-year and bad-url rows are skipped; the two well-formed rows survive.
        assertThat(entries)
                .extracting(ImdbEntry::name, ImdbEntry::imdbId)
                .containsExactly(
                        tuple("Good One", ImdbId.of("tt0000001")),
                        tuple("Good Two", ImdbId.of("tt0000004")));
    }
}
