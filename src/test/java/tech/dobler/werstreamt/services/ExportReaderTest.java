package tech.dobler.werstreamt.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.dobler.werstreamt.configurations.WerStreamtProperties;
import tech.dobler.werstreamt.entities.ImdbEntry;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ExportReaderTest {

    private static final String LIST_NAME = "2024-12-25_Test.csv";

    private ExportReader exportReader;

    @BeforeEach
    void setUp() {
        final var properties = new WerStreamtProperties(
                "src/test/resources/test-assets",
                new WerStreamtProperties.Invalidate(28));
        exportReader = new ExportReader(properties);
    }

    @Test
    void parsesAllRows() {
        final List<ImdbEntry> entries = exportReader.parse(LIST_NAME);

        assertThat(entries).hasSize(35);
    }

    @Test
    void mapsColumnsOfFirstEntry() {
        final ImdbEntry first = exportReader.parse(LIST_NAME).getFirst();

        final var expected = List.of(
                1,
                "The Prestige",
                "tt0482571",
                URI.create("https://www.imdb.com/title/tt0482571/"),
                "2012-06-22",
                2006,
                true);
        assertThat(first)
                .extracting(
                        ImdbEntry::id,
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
        final List<ImdbEntry> entries = exportReader.parse(LIST_NAME);

        assertThat(entries)
                .extracting(ImdbEntry::imdbId)
                .allMatch(id -> id.startsWith("tt"));
    }

    @Test
    void handlesQuotedTitlesWithApostrophes() {
        final List<ImdbEntry> entries = exportReader.parse(LIST_NAME);

        assertThat(entries)
                .extracting(ImdbEntry::name)
                .contains("Schindler's List", "Ocean's Eleven", "Kill Bill: Vol. 1");
    }

    @Test
    void marksEntriesWithYourRatingAsRated() {
        final List<ImdbEntry> entries = exportReader.parse(LIST_NAME);

        // Every row in the fixture has a "Your Rating" value, so all entries are rated.
        assertThat(entries).allMatch(ImdbEntry::isRated);
    }

    @Test
    void skipsMalformedRowsAndKeepsIdsContiguous(@TempDir Path dir) throws Exception {
        final var csv = """
                Position,Const,Created,Modified,Description,Title,Original Title,URL,Title Type,IMDb Rating,Runtime (mins),Year,Genres,Num Votes,Release Date,Directors,Your Rating,Date Rated
                1,tt0000001,2012-06-22,2012-06-22,,"Good One","Good One",https://www.imdb.com/title/tt0000001/,Movie,8.5,130,2006,"Drama",1,2006-10-20,"Dir",10,2012-06-22
                2,tt0000002,2012-06-22,2012-06-22,,"Bad Year","Bad Year",https://www.imdb.com/title/tt0000002/,Movie,8.5,130,notayear,"Drama",1,2006-10-20,"Dir",10,2012-06-22
                3,tt0000003,2012-06-22,2012-06-22,,"Bad Url","Bad Url",not-an-imdb-url,Movie,8.5,130,2010,"Drama",1,2006-10-20,"Dir",10,2012-06-22
                4,tt0000004,2012-06-22,2012-06-22,,"Good Two","Good Two",https://www.imdb.com/title/tt0000004/,Movie,8.5,130,2011,"Drama",1,2006-10-20,"Dir",10,2012-06-22
                """;
        Files.writeString(dir.resolve("list.csv"), csv);
        final var reader = new ExportReader(
                new WerStreamtProperties(dir.toString(), new WerStreamtProperties.Invalidate(28)));

        final List<ImdbEntry> entries = reader.parse("list.csv");

        // Bad-year and bad-url rows are skipped; surviving entries keep contiguous ids.
        assertThat(entries)
                .extracting(ImdbEntry::name, ImdbEntry::imdbId, ImdbEntry::id)
                .containsExactly(
                        tuple("Good One", "tt0000001", 1),
                        tuple("Good Two", "tt0000004", 2));
    }
}
