package tech.dobler.where2stream.architecture;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The four places that name a Node version must name the same one.
 *
 * <p>They had already drifted (TODO-54): {@code .nvmrc} said 24, {@code engines} allowed
 * {@code >=22 <25}, the Dockerfile built on {@code node:24-alpine}, and {@code packageManager}
 * pinned an npm that existed in none of them. That combination is worse than it looks, because
 * {@code .npmrc} sets {@code engine-strict=true}: a toolchain outside {@code engines} does not
 * warn, it fails with {@code EBADENGINE} (verified — not inferred from the setting's name). So
 * divergence here does not degrade the build, it stops it, and the error names the version rather
 * than the file that disagrees.
 *
 * <p>Which Node line we are on, and why it is a single major rather than a range, is ADR-0021.
 *
 * <p>What this test does <em>not</em> do is check the outside world. It cannot tell you that the
 * major we chose has gone end-of-life, or that Angular has moved its floor — only that the
 * repository agrees with itself. Node 25 reached EOL on 2026-06-01 while {@code engines} was still
 * written to exclude it by name; nothing here would have noticed.
 */
class ToolchainVersionsAgreeTest {

    private static final Path REPO = Path.of(".");
    private static final Path FRONTEND = REPO.resolve("src/main/frontend");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern NODE_BASE_IMAGE = Pattern.compile("ARG NODE_BASE_IMAGE=node:(\\d+)");
    private static final Pattern LEADING_MAJOR = Pattern.compile("(\\d+)");

    private static JsonNode json(String fileName) throws IOException {
        return JSON.readTree(FRONTEND.resolve(fileName).toFile());
    }

    /** {@code engines.node} as written — from {@code package.json}, or a lockfile's root entry. */
    private static String enginesNode(JsonNode root) {
        final var engines = root.path("engines").path("node");
        assertThat(engines.isTextual()).as("no engines.node in %s", root.path("name").asText("(lockfile)")).isTrue();
        return engines.asText();
    }

    /** The first number in a version expression — {@code ^24.15.0} and {@code 24} both give 24. */
    private static String majorIn(String expression) {
        final var matcher = LEADING_MAJOR.matcher(expression);
        assertThat(matcher.find()).as("no version number in \"%s\"", expression).isTrue();
        return matcher.group(1);
    }

    private static String dockerfileNodeMajor() throws IOException {
        final var matcher = NODE_BASE_IMAGE.matcher(Files.readString(REPO.resolve("Dockerfile")));
        assertThat(matcher.find()).as("ARG NODE_BASE_IMAGE not found — has the Dockerfile been restructured?").isTrue();
        return matcher.group(1);
    }

    @Test
    void nvmrcEnginesAndDockerfileNameTheSameNodeMajor() throws IOException {
        final var nvmrc = Files.readString(FRONTEND.resolve(".nvmrc")).trim();
        final var engines = enginesNode(json("package.json"));
        final var dockerfile = dockerfileNodeMajor();

        assertThat(new String[]{majorIn(nvmrc), majorIn(engines), dockerfile})
                .as("Node major in .nvmrc (%s), package.json engines (%s) and Dockerfile (%s)",
                        nvmrc, engines, dockerfile)
                .containsOnly(majorIn(nvmrc));
    }

    /**
     * npm copies {@code engines} verbatim into the lockfile's root package entry, and rewrites it
     * only when something makes it regenerate — which editing {@code package.json} alone does not.
     *
     * <p>Not hypothetical: the change that introduced this test left the lockfile saying
     * {@code >=22 <25} while {@code package.json} already said {@code ^24.15.0}, and every other
     * check passed. The Maven build hides it further, because its {@code npm-ci} step is guarded by
     * an {@code uptodate} check against {@code package-lock.json} — so editing only
     * {@code package.json} skips the install altogether and nothing revisits the question.
     */
    @Test
    void packageLockMirrorsTheEnginesFromPackageJson() throws IOException {
        final var declared = enginesNode(json("package.json"));
        final var locked = enginesNode(json("package-lock.json").path("packages").path(""));

        assertThat(locked)
                .as("package-lock.json still records an older engines.node — run"
                        + " `npm install --package-lock-only` after changing it")
                .isEqualTo(declared);
    }

    @Test
    void enginesPinsOneMajorRatherThanASpanOfThem() throws IOException {
        assertThat(enginesNode(json("package.json")))
                .as("engines.node should be a caret range over a single major (ADR-0021): a span like"
                        + " \">=22 <25\" claims support for majors nothing else here is built or tested on")
                .matches("\\^\\d+\\.\\d+\\.\\d+");
    }

    @Test
    void packageJsonDeclaresNoPackageManager() throws IOException {
        assertThat(json("package.json").has("packageManager"))
                .as("packageManager was removed deliberately (ADR-0021): Corepack accepts only an exact"
                        + " version — \"npm@12\", \"npm@^12\" and \"npm@12.x\" are all rejected as \"expected a"
                        + " semver version\" — so the field cannot express a policy, only a snapshot that"
                        + " goes stale. Nothing in this repository invokes Corepack, so it bound nothing")
                .isFalse();
    }
}
