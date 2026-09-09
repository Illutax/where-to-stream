package tech.dobler.where2stream.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every variable documented in {@code .env.example} must actually reach something.
 *
 * <p>{@code W2S_ADMIN_PASSWORD} did not, for months (TODO-63). It was documented, it was handed to
 * the container, and it bound to no property at all — the prefix is {@code w2s.security}, and
 * {@code w2s.admin.password} does not exist. The seeder therefore treated the password as unset,
 * generated a strong one and logged it. Nothing broke, which is exactly why nobody looked: a
 * silent fallback is indistinguishable from a working configuration.
 *
 * <p>The check is deliberately dumb — the name has to appear <em>literally</em> in
 * {@code compose.yml} or in one of the {@code application*.properties}. It does not try to decide
 * whether relaxed binding would have resolved it anyway, and that is the point: a variable whose
 * wiring you cannot grep for is a variable nobody can verify. {@code TMDB_API_KEY} worked that way
 * until this test arrived — correctly, but invisibly, and a break would have fallen back to IMDb
 * without a word.
 *
 * <p>No exemption list, and that is worth a sentence. The first draft carried two —
 * {@code DOCKER_IMAGE_TAG} and {@code MARIADB_ROOT_PASSWORD}, both assumed to be tooling rather
 * than application config. Both turned out to be referenced in {@code compose.yml} anyway. An
 * exemption nobody needs is a place the next reader stops looking.
 *
 * <p>What it cannot check: whether the property on the receiving end is the <em>right</em> one.
 * A variable wired to the wrong property passes here.
 */
class EnvExampleIsWiredUpTest {

    private static final Path REPO = Path.of(".");

    /** A shell-style assignment at the start of a line: `NAME=…`, comments excluded. */
    private static final Pattern ASSIGNMENT = Pattern.compile("^([A-Z][A-Z0-9_]*)=", Pattern.MULTILINE);

    private static String consumers() throws IOException {
        try (var properties = Files.list(REPO.resolve("src/main/resources"))) {
            final var propertyFiles = properties
                    .filter(path -> path.getFileName().toString().matches("application.*\\.properties"))
                    .toList();
            final var texts = Stream.concat(
                    Stream.of(REPO.resolve("compose.yml")),
                    propertyFiles.stream());
            final var joined = new StringBuilder();
            for (var path : texts.toList()) {
                joined.append(Files.readString(path)).append('\n');
            }
            return joined.toString();
        }
    }

    @Test
    void everyDocumentedVariableIsReadSomewhere() throws IOException {
        final var wiring = consumers();

        final var orphans = ASSIGNMENT.matcher(Files.readString(REPO.resolve(".env.example")))
                .results()
                .map(match -> match.group(1))
                .distinct()
                .filter(name -> !wiring.contains(name))
                .toList();

        assertThat(orphans)
                .as(".env.example variables that neither compose.yml nor any application*.properties reads")
                .isEmpty();
    }
}
