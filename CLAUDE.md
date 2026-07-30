# w2s (where-to-stream)

Spring Boot 4 / Java 25 backend + Angular 22 SPA frontend.
Backend is organised by bounded context first, ports & adapters second (see ADR-0014):
`accountaccess`, `watchlist`, `titlecatalog`, `streamingavailability`, each with its own
`domain` → `application` → `port` → `adapter` tree.
`shared` is not itself a bounded context (no `port`/`adapter` split — nothing to protect there),
and splits instead into `shared/kernel` (`ImdbId`, `ReleaseYear` + their adapters — value types
every context needs) and `shared/platform` (`TimeService`, `RateLimiter`, `ApiExceptionHandler`,
the status/SPA-shell endpoints — cross-cutting but not shared *domain* types).
One context may depend on another only through its published `port.in` interface
(e.g. `CurrentUserPort`, `WatchlistCatalogPort`) — enforced per-context by `ArchitectureTest`.

## Collaboration style

- **Honesty over confidence.** State uncertainty and limitations plainly instead of glossing
  over them — e.g. say when something wasn't (or couldn't be) verified, rather than implying
  it was.
- **Work token-sparingly.** Prefer the smallest investigation or change that actually answers
  the question or fixes the issue; avoid redundant re-reading, unnecessary exploration, or
  padding responses.

## Before writing or reviewing code

- **Check `docs/adr/README.md`** for existing architecture decisions before making a design,
  stack, or convention call the project may have already settled (time handling, Optionals,
  domain value objects, OSIV, test libraries, …).
  Don't re-litigate a decision that already has an ADR — extend it if it turns out to be wrong,
  don't just diverge.
- **Check `.claude/skills/`** for a skill matching the task before writing it a different way.
  In particular: **when writing or reviewing a test that checks several fields of the same object with multiple sequential `assertThat(...)` calls, load and apply the `consolidate-test-assertions` skill**
  (`.claude/skills/consolidate-test-assertions/SKILL.md`) — collapse them into one
  `extracting(...).isEqualTo(...)` / `containsExactly(...)` assertion per ADR-0005, instead of a
  run of single-value checks that hides everything after the first failure.
  This applies whether the test is brand new or already exists and is being touched for another
  reason.
- Neither of these is automatically enforced (no lint rule or pre-commit hook greps for the
  anti-patterns yet) — actively check both before considering test/review work done, don't
  wait for a reminder.

## Prose formatting (Markdown docs and code comments)

- **Semantic line breaks**: wrap prose at sentence ends (or clause boundaries for long
  sentences), not at a fixed column width.
  One sentence per line where reasonable; break a long sentence at its commas/clauses rather
  than mid-clause.
  This keeps diffs to the sentence that actually changed instead of reflowing an entire
  paragraph.
- Applies everywhere prose appears: Markdown docs (`README.md`, `TODOs.md`, `docs/adr/*.md`,
  this file) and multi-line Java/TypeScript comments (Javadoc, JSDoc, block comments).
  Don't break inside inline code spans, `{@code}`/`{@link}`, or Markdown links.
- Code itself (statements, expressions) keeps its normal formatting — this rule is about prose only.

## Testing

- Backend: AssertJ + Mockito + JUnit 5 only (ADR-0005).
  No Hamcrest, no JUnit `Assertions.*` in test bodies — both are structurally still on the
  classpath (Testcontainers needs `junit:junit`'s `TestRule` interface at class-load time;
  Spring's `jsonPath(...).value(...)` needs `org.hamcrest.Matcher` resolvable at compile time
  for its overload set) but neither is meant to be used directly;
  see ADR-0005 for why removing them outright breaks the build.
- Frontend: Vitest (ADR-0004).

## Frontend loading state

- **Prefer a loading skeleton over a generic "Lädt…"/"Loading…" spinner.**
  When a page fetches data, show placeholder content shaped like the real thing — skeleton
  tiles/rows with a left-to-right shimmer — instead of swapping the whole page body for a
  spinner: a spinner-to-full-page swap is a large, jarring layout jump once the fetch resolves,
  a skeleton isn't.
- Static chrome that doesn't depend on the fetch (page headings, table column headers, toolbar/
  sort controls, forms) should render immediately; only the data-shaped content itself gates on
  a `loading` input/signal and shows placeholders in its place. Concretely: push a `loading`
  input down into the presentational table/grid component rather than branching the whole page
  between a spinner and the real content.
- Reuse the existing building blocks instead of inventing new ones: the `.skeleton-bar` /
  `.skeleton-bar--narrow` / `.skeleton-rated` CSS classes (`styles.scss`) for table-cell-shaped
  placeholders, and `TitleTileSkeleton` for poster-tile-shaped placeholders.
  `TitleGrid`, `CatalogTable`, `ManageTable`, `FlatrateTable`, and `PaidTable` are reference
  implementations of the `loading` input pattern.
