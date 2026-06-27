package tech.dobler.werstreamt.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tech.dobler.werstreamt.entities.ImdbEntry;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExportReaderTest {

    private static final String LIST_NAME = "2024-12-25_Test.csv";

    private ExportReader exportReader;

    @BeforeEach
    void setUp() {
        exportReader = new ExportReader();
        ReflectionTestUtils.setField(exportReader, "filePath", "src/test/resources/test-assets");
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
}
