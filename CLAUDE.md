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
- **Look up, don't guess — where it's warranted.** We keep the ADRs maintained precisely so
  architecture/convention questions don't have to be guessed and only a handful of specifics
  ever need looking up. Architecture over sprawl: check `docs/adr/README.md` (see below) and
  follow what's documented instead of improvising a parallel approach.

## Project knowledge lives in this repository — not in an agent memory

**Do not use any agent-side persistent memory feature** (Claude Code's `memory/` directory,
`MEMORY.md`, or an equivalent under another name).
Anything worth carrying from one session to the next goes into a **file in this repository**,
in a commit.

Where it belongs:

| What | Where |
| --- | --- |
| Architecture, stack and convention decisions | `docs/adr/` — via the `adr` skill, indexed in `docs/adr/README.md` |
| How we work together, review/test conventions | this file (`CLAUDE.md`) |
| What the project is, how to build and run it | `README.md` |
| Where a human should look for all of the above | `CONTRIBUTING.md` — signpost only, no content of its own |
| Outstanding work | `TODOs.md` — **open items only** |
| Finished and abandoned work | `DONE.md` — history, explicitly **not** maintained |
| A snapshot of how things stand on a given day | `docs/reviews/YYYY-MM-DD-*.md`, never edited afterwards |
| A plan for a larger effort | a document under `docs/` |
| Repeatable, mechanical procedures | `.claude/skills/` |
| Why a specific change was made | the commit message |

The reason is reviewability.
A note in an agent memory is invisible to the human reviewer, is not versioned, does not survive
a fresh clone, cannot be corrected in review, and silently drifts out of sync with the code it
describes.
A committed file is none of those things.
The `Collaboration style` section above exists precisely because those preferences were written
down here instead of being remembered privately.

The practical rule: **if it is worth remembering, it is worth a commit.**
If something genuinely does not fit any of the files above, say so and propose where it should
live — do not fall back to a private note.

## Documentation that stays true

The split between `TODOs.md` and `DONE.md` is not filing, it is what makes the first file
trustworthy. When both lived together, 86 % of the lines were history written in the present
tense — and you could not tell a merely outdated class name from a statement that had become
false without checking each one. A full check of all 63 entries took twelve parallel agents.

So the rules are:

- **`TODOs.md` holds only open work.** Finishing something means *moving* it to `DONE.md`,
  never ticking it off in place. `DocumentationConsistencyTest` fails on a ✅ in `TODOs.md`.
- **`DONE.md` is history and says so.** An entry there pointing at `services/OldName` is
  correct — it describes the world it was written in. Nothing checks that file.
- **Point, don't restate.** Link the authoritative place instead of copying its content.
  Every copy of a fact drifts on its own: `ddl-auto` once stood in three places, two of them
  claiming `validate` long after it had become `none`.
- **A review is a dated snapshot**, filed as `docs/reviews/YYYY-MM-DD-*.md` and not edited
  afterwards. The predecessor drifted precisely because it was undated and therefore read as
  current — it was written one day before the restructuring that invalidated it.
- `DocumentationConsistencyTest` checks paths, links, ticket numbers and status markers on
  every build. It cannot tell whether a *statement* is still true; it only shrinks what a human
  has to re-read.

The routine for both lives in `.claude/skills/` — `ticket` for opening/closing work,
`architecture-review` for the periodic snapshot.

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

## Language

**Everything written into this repository is in English** — code, identifiers, comments, commit
messages, ADRs, tickets, skills, documentation. One language for the artefact, so a reader never
has to guess which one a given file is in, and so a term means the same thing in the code as in
the ticket that describes it.

**This says nothing about how we talk.** The conversation runs in whatever language suits;
switching to German mid-session changes nothing about what gets committed. Do not treat this
section as a request to reply in English.

Two deliberate exceptions, both about *history* rather than neglect:

- `DONE.md`, the plan documents under `docs/`, and `docs/reviews/` stay as they were written.
  They are records, not living text — translating them would edit the past for no reader's benefit.
- Existing German ADRs stay German until one is substantially revised anyway. **New ADRs are
  written in English.** A mixed `docs/adr/` is the price of not spending a day on translation
  that changes no decision.

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
