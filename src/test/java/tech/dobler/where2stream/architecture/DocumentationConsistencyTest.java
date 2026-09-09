package tech.dobler.where2stream.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hält die <em>offene</em> Dokumentation an der Realität fest.
 *
 * <p>Am 2026-09-09 wurden alle 63 TODO-Einträge von Hand gegen den Code geprüft. Der Aufwand war
 * erheblich, und die Mehrheit der Funde war mechanischer Natur: ein Pfad, den es nicht mehr gab,
 * ein Querverweis auf ein Ticket mit falscher Nummer, ein Link auf ein verschobenes Dokument.
 * Genau das prüft diese Klasse — damit dieselbe Runde nicht in einem halben Jahr wieder ansteht.
 *
 * <p><strong>Bewusst nur über {@code TODOs.md}, nicht über {@code DONE.md}.</strong> Ein
 * abgeschlossenes Ticket beschreibt die Welt, in der es geschrieben wurde; dass es auf
 * {@code services/WerStreamtEsApiClient} zeigt, ist dort richtig und nicht falsch. Erst die
 * Trennung der beiden Dateien macht diesen Test überhaupt möglich — vorher hätte er zwischen
 * „veralteter Name" und „veraltete Aussage" unterscheiden müssen, und das kann er nicht.
 *
 * <p>Was er ebenfalls nicht kann: erkennen, ob eine <em>Aussage</em> noch stimmt. Ein Eintrag
 * darf hier grün sein und trotzdem Unsinn behaupten. Der Test verkleinert die Menge dessen, was
 * ein Mensch nachsehen muss; er ersetzt das Nachsehen nicht.
 */
class DocumentationConsistencyTest {

    /** Vom Repository-Wurzelverzeichnis aus, wo Maven den Test startet. */
    private static final Path REPO = Path.of(".");
    private static final Path OPEN_TODOS = REPO.resolve("TODOs.md");
    private static final Path ADR_DIR = REPO.resolve("docs/adr");
    private static final Path ADR_INDEX = ADR_DIR.resolve("README.md");

    /**
     * Ein Pfad in Backticks, der wie eine Datei im Repository aussieht.
     *
     * <p>Verlangt eine bekannte Endung. Ohne diese Einschränkung würde jeder Ausdruck mit
     * Schrägstrich als Pfad gelten — {@code `port.in`/`port.spi`}, {@code `24/7`}, {@code `and/or`} —
     * und der Test wäre nur noch Rauschen.
     */
    private static final Pattern FILE_REFERENCE = Pattern.compile(
            "`([A-Za-z0-9_./-]+\\.(?:java|ts|html|scss|json|xml|yaml|yml|properties|md|sh))`");

    /** Ein Markdown-Link auf eine Datei im Repository (keine http-URL). */
    private static final Pattern MARKDOWN_LINK = Pattern.compile("]\\((?!https?://)([^)#]+)");

    private static final Pattern TODO_HEADING = Pattern.compile("^### (\\S+) (TODO-\\d+) — ", Pattern.MULTILINE);
    private static final Pattern TODO_REFERENCE = Pattern.compile("\\bTODO-(\\d+)\\b");

    /** Die Marker, die {@code TODOs.md} im Kopf als Priorität erklärt. Erledigtes gehört nach DONE.md. */
    private static final Set<String> OPEN_STATUS_MARKERS = Set.of("🔴", "🟠", "🟡", "🟢");

    private String openTodos() throws IOException {
        return Files.readString(OPEN_TODOS);
    }

    private static List<String> matches(Pattern pattern, String text, int group) {
        return pattern.matcher(text).results().map(m -> m.group(group)).distinct().toList();
    }

    @Test
    void everyFileTheOpenTodosPointAtExists() throws IOException {
        final var missing = matches(FILE_REFERENCE, openTodos(), 1).stream()
                .filter(reference -> !Files.exists(REPO.resolve(reference)))
                .toList();

        // Die häufigste Driftform von allen: der Code wird umbenannt, das Ticket nicht.
        assertThat(missing).as("in TODOs.md referenzierte Dateien, die es nicht gibt").isEmpty();
    }

    @Test
    void everyDocumentTheOpenTodosLinkToExists() throws IOException {
        final var broken = matches(MARKDOWN_LINK, openTodos(), 1).stream()
                .filter(link -> !Files.exists(REPO.resolve(link)))
                .toList();

        assertThat(broken).as("tote Markdown-Links in TODOs.md").isEmpty();
    }

    @Test
    void everyDocumentTheEntryPointsLinkToExists() throws IOException {
        // README.md und CONTRIBUTING.md sind die Türen ins Repository; ein toter Link darin
        // empfängt jeden Ankömmling. Geprüft werden nur Markdown-Links auf Dateien — Prosa und
        // Beispiel-URLs bleiben außen vor, sonst wäre der Test Rauschen.
        final var broken = Stream.of("README.md", "CONTRIBUTING.md").flatMap(file -> {
            final String text;
            try {
                text = Files.readString(REPO.resolve(file));
            } catch (IOException e) {
                throw new IllegalStateException(file, e);
            }
            return MARKDOWN_LINK.matcher(text).results()
                    .map(match -> match.group(1))
                    .filter(link -> !Files.exists(REPO.resolve(link)))
                    .map(link -> file + " -> " + link);
        }).toList();

        assertThat(broken).as("tote Markdown-Links in README.md / CONTRIBUTING.md").isEmpty();
    }

    @Test
    void everyCrossReferencedTicketExistsInEitherFile() throws IOException {
        // Ein Verweis darf auf ein erledigtes Ticket zeigen — TODO-59 verweist zu Recht auf das
        // abgeschlossene TODO-57. Nur ins Nichts darf er nicht zeigen.
        final var known = Stream.concat(
                        TODO_HEADING.matcher(openTodos()).results().map(m -> m.group(2)),
                        TODO_HEADING.matcher(Files.readString(REPO.resolve("DONE.md")))
                                .results().map(m -> m.group(2)))
                .collect(java.util.stream.Collectors.toSet());

        final var dangling = matches(TODO_REFERENCE, openTodos(), 0).stream()
                .filter(reference -> !known.contains(reference))
                .toList();

        assertThat(dangling).as("Verweise auf Tickets, die weder offen noch erledigt sind").isEmpty();
    }

    @Test
    void openTicketsHaveUniqueIdsAndAPriorityMarker() throws IOException {
        final var headings = TODO_HEADING.matcher(openTodos()).results().toList();
        final var ids = headings.stream().map(m -> m.group(2)).toList();
        final var wrongMarker = headings.stream()
                .filter(m -> !OPEN_STATUS_MARKERS.contains(m.group(1)))
                .map(m -> m.group(2) + " (" + m.group(1) + ")")
                .toList();

        // Ein ✅ in dieser Datei heißt: jemand hat abgehakt statt zu verschieben — und damit
        // fängt die Vermischung wieder an, die den Drift erzeugt hat.
        assertThat(List.of(ids.stream().distinct().count() == ids.size(), wrongMarker))
                .as("eindeutige Ticket-Nummern; nur Prioritätsmarker, kein ✅/❌ in TODOs.md")
                .isEqualTo(List.of(true, List.of()));
    }

    @Test
    void theAdrIndexListsExactlyTheAdrsThatExist() throws IOException {
        try (var files = Files.list(ADR_DIR)) {
            final var onDisk = files.map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("\\d{4}-.*\\.md"))
                    .sorted()
                    .toList();
            final var indexed = matches(MARKDOWN_LINK, Files.readString(ADR_INDEX), 1).stream()
                    .filter(link -> link.matches("\\d{4}-.*\\.md"))
                    .sorted()
                    .toList();

            // Eine ADR, die niemand im Index findet, ist eine Entscheidung, die niemand kennt.
            assertThat(indexed).as("docs/adr/README.md gegen die vorhandenen ADR-Dateien").isEqualTo(onDisk);
        }
    }

    @Test
    void everyDocumentAnAdrLinksToExists() throws IOException {
        try (var files = Files.list(ADR_DIR)) {
            final var broken = files.filter(path -> path.toString().endsWith(".md")).flatMap(path -> {
                final String text;
                try {
                    text = Files.readString(path);
                } catch (IOException e) {
                    throw new IllegalStateException(path.toString(), e);
                }
                return MARKDOWN_LINK.matcher(text).results()
                        .map(match -> match.group(1))
                        .filter(link -> !Files.exists(ADR_DIR.resolve(link).normalize()))
                        .map(link -> path.getFileName() + " -> " + link);
            }).toList();

            // ADRs verlinken einander und die Plan-/Review-Dokumente; ein Umzug wie der von
            // ARCHITECTURE_REVIEW.md nach docs/reviews/ bricht das sonst unbemerkt.
            assertThat(broken).as("tote Links in ADRs").isEmpty();
        }
    }
}
