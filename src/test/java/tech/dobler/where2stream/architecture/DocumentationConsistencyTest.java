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
 * Keeps the <em>open</em> documentation tied to reality.
 *
 * <p>On 2026-09-09 all 63 TODO entries were checked against the code by hand. It cost a day, and
 * most of what it found was mechanical: a path that no longer existed, a cross-reference to the
 * wrong ticket number, a link to a document that had moved. That is what this class checks — so
 * the same exercise is not due again in six months.
 *
 * <p><strong>Deliberately over {@code TODOs.md} only, never {@code DONE.md}.</strong> A finished
 * ticket describes the world it was written in; that it points at
 * {@code services/WerStreamtEsApiClient} is correct there, not wrong. Splitting the two files is
 * what makes this test possible at all — before that it would have had to tell "outdated name"
 * from "outdated claim", and it cannot.
 *
 * <p>What it also cannot do: notice that a <em>statement</em> has stopped being true. An entry may
 * be green here and still talk nonsense. The test shrinks what a human has to re-read; it does not
 * replace the reading.
 */
class DocumentationConsistencyTest {

    /** Relative to the repository root, which is where Maven starts the test. */
    private static final Path REPO = Path.of(".");
    private static final Path OPEN_TODOS = REPO.resolve("TODOs.md");
    private static final Path ADR_DIR = REPO.resolve("docs/adr");
    private static final Path ADR_INDEX = ADR_DIR.resolve("README.md");

    /**
     * A backticked path that looks like a file in the repository.
     *
     * <p>A known extension is required. Without that restriction anything containing a slash would
     * count as a path — {@code `port.in`/`port.spi`}, {@code `24/7`}, {@code `and/or`} — and the
     * test would be pure noise.
     */
    private static final Pattern FILE_REFERENCE = Pattern.compile(
            "`([A-Za-z0-9_./-]+\\.(?:java|ts|html|scss|json|xml|yaml|yml|properties|md|sh))`");

    /** A Markdown link to a file in the repository (not an http URL). */
    private static final Pattern MARKDOWN_LINK = Pattern.compile("]\\((?!https?://)([^)#]+)");

    private static final Pattern TODO_HEADING = Pattern.compile("^### (\\S+) (TODO-\\d+) — ", Pattern.MULTILINE);
    private static final Pattern TODO_REFERENCE = Pattern.compile("\\bTODO-(\\d+)\\b");

    /** The markers {@code TODOs.md} declares as priorities. Anything finished belongs in DONE.md. */
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

        // The most common kind of drift by far: the code gets renamed, the ticket does not.
        assertThat(missing).as("files referenced in TODOs.md that do not exist").isEmpty();
    }

    @Test
    void everyDocumentTheOpenTodosLinkToExists() throws IOException {
        final var broken = matches(MARKDOWN_LINK, openTodos(), 1).stream()
                .filter(link -> !Files.exists(REPO.resolve(link)))
                .toList();

        assertThat(broken).as("dead Markdown links in TODOs.md").isEmpty();
    }

    @Test
    void everyDocumentTheEntryPointsLinkToExists() throws IOException {
        // README.md and CONTRIBUTING.md are the doors into the repository; a dead link there is
        // what greets every arrival. Only Markdown links to files are checked — prose and example
        // URLs stay out of it, or the test would be noise.
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

        assertThat(broken).as("dead Markdown links in README.md / CONTRIBUTING.md").isEmpty();
    }

    @Test
    void everyCrossReferencedTicketExistsInEitherFile() throws IOException {
        // A reference may point at a finished ticket — TODO-59 rightly cites the closed TODO-57.
        // It just must not point at nothing.
        final var known = Stream.concat(
                        TODO_HEADING.matcher(openTodos()).results().map(m -> m.group(2)),
                        TODO_HEADING.matcher(Files.readString(REPO.resolve("DONE.md")))
                                .results().map(m -> m.group(2)))
                .collect(java.util.stream.Collectors.toSet());

        final var dangling = matches(TODO_REFERENCE, openTodos(), 0).stream()
                .filter(reference -> !known.contains(reference))
                .toList();

        assertThat(dangling).as("references to tickets that are neither open nor done").isEmpty();
    }

    @Test
    void openTicketsHaveUniqueIdsAndAPriorityMarker() throws IOException {
        final var headings = TODO_HEADING.matcher(openTodos()).results().toList();
        final var ids = headings.stream().map(m -> m.group(2)).toList();
        final var wrongMarker = headings.stream()
                .filter(m -> !OPEN_STATUS_MARKERS.contains(m.group(1)))
                .map(m -> m.group(2) + " (" + m.group(1) + ")")
                .toList();

        // A ✅ in this file means somebody ticked off instead of moving — and that is exactly how
        // the mixing that produced the drift starts again.
        assertThat(List.of(ids.stream().distinct().count() == ids.size(), wrongMarker))
                .as("unique ticket numbers; priority markers only, no ✅/❌ in TODOs.md")
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

            // An ADR nobody finds in the index is a decision nobody knows about.
            assertThat(indexed).as("docs/adr/README.md against the ADR files that exist").isEqualTo(onDisk);
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

            // ADRs link each other and the plan/review documents; a move like ARCHITECTURE_REVIEW.md
            // to docs/reviews/ breaks that silently otherwise.
            assertThat(broken).as("dead links in ADRs").isEmpty();
        }
    }
}
