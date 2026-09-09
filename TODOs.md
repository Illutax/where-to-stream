# TODOs — open work

**Only what still needs doing lives here.** Finished work moves to [`DONE.md`](DONE.md).

That split is not tidiness, it is the reason this file can be trusted. While finished entries sat
here too, 86 % of the lines were history written in the present tense — and you could not tell an
outdated class name from a claim that had become false without checking each one. Now:
**what is in here is true now.**

Priority: 🔴 high · 🟠 medium · 🟡 medium-low · 🟢 low

## Rules for entries

1. **Point, don't restate.** Link the authoritative place (ADR, property, class) instead of
   copying its content. Every copy of a fact drifts on its own — `ddl-auto` once stood in three
   places, two of them wrong.
2. **Cite paths**, not "somewhere in the service" — the way TODO-22 points at
   `src/main/java/tech/dobler/where2stream/watchlist/application/ExportReader.java`.
   `DocumentationConsistencyTest` checks at build time that the file exists; a backticked path
   that does not resolve turns the build red.
3. **Mark the unverified as unverified.** "Not verified:" is a complete statement; a guess that
   reads like a finding costs more later than it saves.
4. **When finishing:** move the entry to [`DONE.md`](DONE.md), do not tick it off here. If the
   reasoning has lasting value it belongs in an ADR first — the archive is not maintained.

The full routine is a skill: [`.claude/skills/ticket/SKILL.md`](.claude/skills/ticket/SKILL.md).

---

## Overview

| | Ticket | Summary |
| --- | --- | --- |
| 🔴 | [TODO-54](#todo-54) | Pin the Node/npm version in one authoritative place |
| 🟠 | [TODO-65](#todo-65) | A new architecture review, as a dated snapshot |
| 🟠 | [TODO-66](#todo-66) | Bring resilience4j back, for the outbound adapters |
| 🟠 | [TODO-68](#todo-68) | `ImdbSearchApiController` validates in the controller body (breaks ADR-0015) |
| 🟡 | [TODO-59](#todo-59) | `/api/titles/{id}/meta`: one request per row, never cancelled |
| 🟢 | [TODO-22](#todo-22) | Hard-coded CSV header array |
| 🟢 | [TODO-42](#todo-42) | No minimum length or complexity for passwords |
| 🟢 | [TODO-52](#todo-52) | Reduce the Angular bundle (trigger: 1 MB initial bundle) |
| 🟢 | [TODO-60](#todo-60) | Serve `PaidEntryDto.year` as a number |
| 🟢 | [TODO-69](#todo-69) | The settings test reaches into `MatSelect` internals (breaks ADR-0004) |

---

## 🔴 High

### 🔴 TODO-54 — Pin the Node/npm version in one authoritative place
The permitted toolchain is stated in **four** places, maintained separately, and they have already
diverged:

| Place | Says | As of 2026-09-06 |
| --- | --- | --- |
| `src/main/frontend/.nvmrc` | `24` | major only |
| `src/main/frontend/package.json` → `engines` | `node >=22 <25`, `npm >=10` | a range |
| `src/main/frontend/package.json` → `packageManager` | `npm@11.16.0` | **exact, and stale** |
| `Dockerfile` → `NODE_BASE_IMAGE` | `node:24-alpine` | major pinned, minor/patch floating |

`src/main/frontend/.npmrc` sets `engine-strict=true`, so a toolchain outside the range aborts
`npm ci` **hard**. That is right, but it means any divergence stops the build dead.

**What currently holds** (checked against the registry, not guessed):

- Angular 22.0.7 requires `node ^22.22.3 || ^24.15.0 || >=26.0.0` — **Node 25 is explicitly
  excluded**, the range jumps from 24 to 26.
- `npm` is at **12.0.2**; the last 11.x is 11.19.1. The `11.16.0` pinned in `packageManager` is
  therefore not current anywhere, neither in the Node 24 line nor elsewhere.
- Angular itself is at 22.1.5, the project at 22.0.7 — one minor behind, not a problem.

- **Acceptance:** one source of truth for Node and npm, from which the other places are derived or
  against which they are checked. At minimum: either maintain `packageManager` or drop it, and pin
  `NODE_BASE_IMAGE` to the same range as `engines`.
- **To decide:** whether `engines` stays at `>=22 <25` (then every Node bump to 25 has to be
  blocked deliberately) or moves to Angular's own range (`^22.22.3 || ^24.15.0 || >=26.0.0`),
  which models the gap at 25 correctly.
- **Related to TODO-55** (in `DONE.md`): the version question only became urgent because the
  auto-upgrade run hits it unguarded.

---

## 🟠 Medium

### 🟠 TODO-65 — A new architecture review, as a dated snapshot
Its predecessor ([`docs/reviews/2026-07-28-architecture-review.md`](docs/reviews/2026-07-28-architecture-review.md))
is dated the day **before** [ADR-0014](docs/adr/0014-backend-by-bounded-context-and-ports-adapters.md).
It triggered the restructuring that then invalidated it, and no successor has been written since.

**The form matters more than the cadence.** A review is a **snapshot with a date in its filename**,
not a living document. That is exactly where the predecessor failed: it sat undated under `docs/`
and was read as describing the present. A snapshot that carries its date is allowed to age.

- **Location:** `docs/reviews/`, named **YYYY-MM-DD-architecture-review.md**, **not edited**
  after writing (typos excepted).
- **The output is actions, not prose:** what keeps applying becomes an **ADR**, what needs doing
  becomes a **TODO**. The review document only carries the finding and its reasoning. Without that
  rule you get a third document drifting away from the other two.
- **Scope:** the four bounded contexts and their boundaries, `shared`, the frontend structure, the
  ArchUnit rules (do they still cover what they should?), and explicitly the question of which of
  the 20 ADRs no longer describe reality.
- **Run it only after TODO-64** (in `DONE.md`) — otherwise the review examines documentation we
  already know to be wrong.
- **Repeatable:** the procedure is a skill,
  [`.claude/skills/architecture-review/SKILL.md`](.claude/skills/architecture-review/SKILL.md),
  so the next run is not reinvented.

- **Acceptance:** a dated document under `docs/reviews/` describing the current state, with every
  resulting action captured as a TODO or an ADR rather than as an open list inside the review.

### 🟠 TODO-66 — Bring resilience4j back, for the outbound adapters
Removing `purchaseoffers` (TODO-56) removed the only user of `resilience4j-spring-boot4`, and the
dependency went with it. The application has had **no** circuit breaker since.

**The need did not disappear, only the user did.** Three adapters talk to third-party services
that genuinely go down:

| Adapter | Service | Today |
| --- | --- | --- |
| `src/main/java/tech/dobler/where2stream/streamingavailability/adapter/out/werstreamtes/WerStreamtEsSource.java` | werstreamt.es (scraping) | `try/catch` per call, `RateLimiter` |
| `src/main/java/tech/dobler/where2stream/titlecatalog/adapter/out/imdb/ImdbTitleSource.java` | IMDb | `try/catch` per call |
| `src/main/java/tech/dobler/where2stream/titlecatalog/adapter/out/tmdb/TmdbPosterSource.java` | TMDB | `try/catch` per call |

A `try/catch` absorbs the individual failure, but it **does not stop asking**. With a service down
for a while, every call runs into the timeout again — and `PreCacheService` and `RefreshService`
fan out over `parallelStream`, as does the background job. That is what a breaker is for: trip on
a failure rate, then retry later with two probe calls.

**What carries over from the first attempt** (written up in TODO-51 in [`DONE.md`](DONE.md)): the
Boot 4 artefact is **`2.4.0`**, not `2.3.0`; and the configuration belongs in Java rather than in
properties, because `src/test/resources/application.properties` shadows the production file on the
test classpath and a typo in a class-name string falls back to the defaults silently.

**To decide:** one breaker per service (three) or one shared. Separate, I would say — a TMDB
outage should not take the availability lookup down with it.

- **Acceptance:** each of the three adapters has its own breaker, the configuration lives in Java,
  and a test per adapter shows that the breaker opens under sustained failure — and that an open
  breaker does **not** break the page, but lands in the same state a single failure does today.

### 🟠 TODO-68 — `ImdbSearchApiController` validates in the controller body
[ADR-0015](docs/adr/0015-self-validating-commands-instead-of-scattered-request-validation.md)
moved request validation out of controllers and into self-validating command records. It found the
pattern at nine places and removed all of them — except this one, which was written days before the
ADR and simply missed:

`src/main/java/tech/dobler/where2stream/titlecatalog/adapter/in/api/ImdbSearchApiController.java`
still throws `ValidationException` from the handler body when `q` is blank, and
`ImdbSearchService.search(UUID, String)` takes loose parameters instead of a command.

**This is the interesting kind of finding**, which is why it is a ticket and not a footnote: the
ADR reads as though the pattern is gone. It is not — it survived in the one place nobody looked,
and nothing would have told us. A rule enforced by having tidied up once is not enforced.

- **Acceptance:** an `ImdbSearchCommand(UUID userId, String query)` that validates itself, the
  controller reduced to mapping, and the `ValidationException` gone from the handler body.
- **Worth considering while there:** whether an ArchUnit rule can catch the general case —
  "no class in `adapter.in.api` throws `ValidationException`" would have found this one.

---

## 🟡 Medium-low

### 🟡 TODO-59 — `/api/titles/{id}/meta`: one request per row, never cancelled
Measured (not estimated) during the TODO-57 review, with 300 tiles:
`injectTitleMeta` (`src/main/frontend/src/app/core/title-meta.ts`) fires **one GET per row** as
soon as age ratings **or** German titles are switched on — age ratings default to on, so that is
the normal case.

Two separate problems:

1. **Nothing is cancelled.** The `subscribe()` inside the `effect()` hangs off no destroy hook.
   After `fixture.destroy()`, **0 of 300** requests had been cancelled. The dashboard's view
   toggle (`@if (viewMode() === 'GRID')`) destroys every row and rebuilds it — one switch back and
   forth is 600 requests, 300 of them orphaned.
2. **No dedup, no batch.** Every row asks separately, with no client cache. Over HTTP/1.1 that is a
   six-deep queue with head-of-line blocking.

**Pre-existing, not caused by TODO-57** — the search link only reads the signal and triggers
nothing extra (measured). Recorded here so the finding does not disappear with the review.

**To do:**
- `takeUntilDestroyed()` / `DestroyRef` in `injectTitleMeta` — fixes point 1 on its own.
- For point 2, a batch endpoint `/api/titles/meta?ids=…` that the page calls once.

**Point 2 is partly done (2026-09-07):** `TitleMetaApi` now holds one shared signal per `ImdbId`
plus an in-flight guard, because the new eBay column would otherwise have caused a **second**
fetch per row — the column would have paid for itself in traffic. A title now costs one request no
matter how many components show it, and switching views re-fetches nothing.
**Still open:** n rows are still n requests (that needs the batch endpoint), and nothing is
cancelled — point 1 is untouched.

- **Acceptance:** switching views leaves no requests in flight; a dashboard with n rows no longer
  produces n metadata requests.

---

## 🟢 Low

### 🟢 TODO-69 — The settings test reaches into `MatSelect` internals
[ADR-0004](docs/adr/0004-vitest-as-the-angular-test-runner.md) chose Vitest with jsdom, and named
the price: jsdom has no layout, so Material overlays need component harnesses. It listed one
countermeasure by name — use a native `<select matNativeControl>` instead of `mat-select`.

Both halves have quietly come apart:

- `matNativeControl` no longer exists anywhere in the repository; it went with the watchlist-import
  rework. `src/main/frontend/src/app/features/settings/settings-page.ts` uses `mat-select` again.
- Its test does not use a harness either. It queries `By.directive(MatSelect)`, calls `open()` and
  reads `.options` — component internals, which is exactly what the ADR warns against. There is no
  `MatSelectHarness` anywhere in the project.

The test is commented and it works. Nobody weighed it against the ADR, though, and that is the
part worth fixing: either use `MatSelectHarness` here, or record in ADR-0004 that harnesses are the
rule with a named exception. Both are fine; the current silence is not.

- **Acceptance:** either the test goes through a harness, or ADR-0004 says why it does not.

### 🟢 TODO-22 — Hard-coded CSV header array
`watchlist/application/ExportReader.headers`: 18 fixed column names, and the file's **real** header
row is discarded via `setSkipHeaderRecord(true)` — the mapping is purely **positional**.

**The damage is worse than "fails silently" suggests.** A completely foreign format fails loudly:
every row falls through and `WatchlistImportService` throws `InvalidImportException`. The dangerous
case is in between — IMDb inserts **one** column or reorders them. Then `record.get("Title")`
silently reads the wrong field, rows with a valid `tt…` link pass, and because the import is a
**full sync**, existing entries are deleted for appearing absent from the misread file.

- **Acceptance:** read the header from the file
  (`CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)` — commons-csv 1.10) and
  validate the five columns actually needed (`Created`, `Title`, `Year`, `Your Rating`, `URL`)
  **once** against it, with a message that says what is missing. Otherwise the user only gets the
  generic "No valid entries found".

### 🟢 TODO-42 — No minimum length or complexity for passwords
`CreateUserCommand` / `ResetPasswordCommand` check only for non-blank in their compact
constructors — no minimum length, no complexity, so an ADMIN can give an account a one-character
password. (The validation used to live in `UserAdminService`; the service only encodes now.)
The initial admin password (`w2s.security.initial-admin.password`) is unchecked in the same way.

- **Acceptance:** sensible minimum requirements (length, possibly character classes), enforced
  server-side, with the message surfaced in the frontend.
- **The placeholder itself is not the problem:** `W2S_ADMIN_PASSWORD=change-me-please` in
  `.env.example` follows that file's established "please change me" convention (see
  `MARIADB_ROOT_PASSWORD=change-me` right below it). That the variable reached no property at all
  was a separate bug, fixed under TODO-63.

### 🟢 TODO-52 — Reduce the Angular bundle (trigger: 1 MB initial bundle)
**Not now.** The initial bundle is at **657.75 kB raw / 146.74 kB** estimated compressed (measured
2026-09-09). That is accepted deliberately; this ticket collects the measured levers for the day it
gets tight.

**Worth noting:** removing the eBay price lookup (TODO-56) did **not** shrink the initial bundle —
655.85 kB before, 657.75 kB after. The removed code lived entirely in lazy chunks, and the new
search link costs a few hundred bytes more there than the old widget did. Anyone hoping a cleanup
will save initial bytes is looking in the wrong place: that chunk is Angular, Material and
Transloco, not our features.

**The trigger lives in the code, not in this text:** `src/main/frontend/angular.json` fails the
build once the initial bundle reaches **1 MB** (`budgets[type=initial].maximumError`), with a
warning from 950 kB. Whoever hits that lands here via the comment there.

**Measurement from 2026-09-05** (esbuild metafile, `ng build --stats-json`):

| Share of `main.js` | Package |
| --- | --- |
| 147.0 kB (24 %) | `@angular/core` |
| 131.0 kB (21 %) | `@angular/material` |
| 107.2 kB (18 %) | `@angular/cdk` |
| 76.5 kB (12 %) | `@angular/router` |
| **23.8 kB (3.9 %)** | **our own application code** |

The most important finding first: **our own code is 3.9 %.** Optimising it is ineffective by
construction. The only lever is which framework surface ends up in the *initial* chunk.

**Four levers, in this order:**

1. **Check the metric before the code.** The budget is set on the raw size; users download the
   compressed one. Before anyone hunts bytes, decide which number we actually want to manage —
   otherwise you optimise against the wrong one.
2. **Get the search box and its dialog out of the app shell — the big lever.**
   `src/main/frontend/src/app/app.ts` loads `ImdbSearchBox` eagerly; that injects `MatDialog` and
   pulls `material/dialog`, `cdk/dialog` **and** `cdk/overlay` into the initial chunk — for a
   dialog that only opens after somebody has typed *and* clicked a result.
   **Measured** by removing it and rebuilding: **−93.98 kB raw / −18.45 kB compressed**, 15 % of
   `main.js`; overlay and dialog leave the initial chunk entirely.
   Implementation: `@defer (on interaction)` around the search box, or move the dialog opening into
   a dynamically imported part.
   **The price:** the search box sits visibly in the toolbar, and `on interaction` means a small
   delay on the first click into it. A deliberate UX trade, not a pure win.
3. **Restrict the font subsets to `latin`/`latin-ext`.**
   `src/main/frontend/angular.json` includes `@fontsource/roboto/{400,500,700}.css` — **all**
   subsets. 768 kB of fonts ship: cyrillic (165 kB), math (115 kB), greek (65 kB), symbols
   (57 kB), vietnamese (43 kB) — none of which a DE/EN interface ever needs. Users do not download
   them thanks to `unicode-range`, but they sit in the deployment and in the image. On top of that,
   **54 % of the initial stylesheet** is `@font-face` rules (14.8 kB of 26.9 kB), only 2.3 kB of it
   latin/latin-ext. Expected: ~12 kB less initial CSS, ~440 kB smaller artefact.
4. **Then measure again** and, if it is still too large, raise the limit deliberately rather than
   working around it.

**What is explicitly not the answer:** swapping Angular Material for hand-written components
(238 kB against a permanent maintenance and accessibility debt), or splitting further just to hit
a number.

### 🟢 TODO-60 — Serve `PaidEntryDto.year` as a number
`PaidEntryDto` (`streamingavailability/application/dto`) formats the year on the server
(`imdbEntry.year().display()`), so it ships `"Not yet released"` as text. Two consequences, both
found during the TODO-57 review:

- **The client cannot compute with it.** `TileEntry.releaseYear` is nullable only for this reason —
  a finished string cannot be turned back into a year without guessing. The eBay search link is the
  first case that depends on it, probably not the last.
- **The text is untranslated English** and lands that way in a bilingual interface, while the
  client already carries the same constant in
  `src/main/frontend/src/app/core/domain.ts`.

`OverviewEntryDto` and `FlatrateEntryDto` already do it right and return `ReleaseYear`.

- **Acceptance:** `PaidEntryDto.year` is a number, the formatting happens client-side via
  `releaseYearDisplay`, and `TileEntry.releaseYear` is no longer nullable.
