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
2. **Cite paths**, not "somewhere in the service" — the way TODO-66 names
   `src/main/java/tech/dobler/where2stream/titlecatalog/adapter/out/imdb/ImdbTitleSource.java`.
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
| 🔴 | [TODO-72](#todo-72) | A failed scrape is cached as fresh "available nowhere" for 28 days |
| 🟠 | [TODO-66](#todo-66) | Bring resilience4j back, for the outbound adapters |
| 🟠 | [TODO-73](#todo-73) | A refresh burst leaks the in-flight tracker until restart |
| 🟠 | [TODO-75](#todo-75) | A poster/metadata outage is negative-cached for 14 days |
| 🟠 | [TODO-76](#todo-76) | The navbar IMDb search dies permanently after one failed request |
| 🟠 | [TODO-77](#todo-77) | `query_meta` generations accumulate forever; every page view loads all of them |
| 🟡 | [TODO-59](#todo-59) | `/api/titles/{id}/meta`: one request per row, never cancelled |
| 🟡 | [TODO-78](#todo-78) | Serving a poster thumbnail loads the full-size BLOB too |
| 🟡 | [TODO-79](#todo-79) | `shared/platform` hosts single-context classes; ADR-0014 and ADR-0019 contradict each other |
| 🟡 | [TODO-80](#todo-80) | The SecurityContext ArchUnit rule no longer covers the packages it targets |
| 🟢 | [TODO-42](#todo-42) | No minimum length or complexity for passwords |
| 🟢 | [TODO-52](#todo-52) | Reduce the Angular bundle (trigger: 1 MB initial bundle) |
| 🟢 | [TODO-81](#todo-81) | "Clear entire watchlist" runs without confirmation |
| 🟢 | [TODO-82](#todo-82) | Hardcoded English user-facing strings bypass Transloco |
| 🟢 | [TODO-83](#todo-83) | Small clean-up finds from the 2026-09-10 architecture review |

---

## 🔴 High

### 🔴 TODO-72 — A failed scrape is cached as fresh "available nowhere" for 28 days
`src/main/java/tech/dobler/where2stream/streamingavailability/adapter/out/werstreamtes/WerStreamtEsSource.java`
maps any `HttpStatusException` (404, 429, 503, …) to an empty list,
and a site-wide markup change makes `parse` return the same empty list —
with **zero** log signal if the top-level selector stops matching.
`StreamInfoService.fetch`
(`src/main/java/tech/dobler/where2stream/streamingavailability/application/StreamInfoService.java`)
then persists that empty result as a fresh, non-invalidated row that **overwrites** previously good
availability data for the next 28 days.
"Scrape failed", "markup changed" and "genuinely available nowhere" are stored identically;
only a *transport* failure (which throws `ScrapingException`) correctly leaves the old row alone.
Full reasoning: F14 in
[`docs/reviews/2026-09-10-architecture-review.md`](docs/reviews/2026-09-10-architecture-review.md).

The fix needs a decision first — [ADR-0012](docs/adr/0012-permanent-title-cache-vs-ttl-availability-cache.md)
and [ADR-0016](docs/adr/0016-asynchronous-deferred-cache-refresh.md) are silent on failure
semantics, so the chosen rule ("a failure result is never persisted as data") belongs in an ADR
extension alongside the code change.

- **Acceptance:** an HTTP-status failure or an empty parse of a structurally unexpected document
  does not replace an existing availability row; a dead top-level selector is visible in the logs;
  a test per case pins it.

## 🟠 Medium

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

### 🟠 TODO-73 — A refresh burst leaks the in-flight tracker until restart
The refresh executor is 2 threads over a 200-deep queue with the default abort policy
(`src/main/java/tech/dobler/where2stream/shared/platform/concurrency/AsyncConfig.java` —
no `RejectedExecutionHandler` exists anywhere).
`src/main/java/tech/dobler/where2stream/streamingavailability/application/BackgroundCacheRefreshService.java`
marks **all** due ids in-flight first, then submits one by one:
submission #203 throws `TaskRejectedException`, aborts the loop, and every already-marked id whose
task never ran stays in
`src/main/java/tech/dobler/where2stream/shared/platform/concurrency/RefreshInFlightTracker.java`
**forever** (it has no expiry) — those titles are never refreshed again until an app restart.
Bulk invalidation from *Manage cache* makes all invalidated titles due at once and bypasses the
jitter, so >202 due titles is a realistic scenario.
The demand-driven path in `StreamInfoService` has the same tryStart-then-submit shape.
Details: F16 in
[`docs/reviews/2026-09-10-architecture-review.md`](docs/reviews/2026-09-10-architecture-review.md).

- **Acceptance:** a rejected submission releases its tracker entry (or submission cannot be
  rejected, e.g. caller-runs/bounded batching), and a test shows a burst larger than the queue
  leaves no id permanently in flight.

### 🟠 TODO-75 — A poster/metadata outage is negative-cached for 14 days
Same principle as [TODO-72](#todo-72), titlecatalog side.
`discover` in `src/main/java/tech/dobler/where2stream/titlecatalog/application/PosterService.java`
stores `findPosterPath(...).orElse(null)` unconditionally, collapsing "fetch failed" into
"title has no poster" — a fresh negative for `poster.negative-cache-days` (14 d).
`src/main/java/tech/dobler/where2stream/titlecatalog/application/TitleMetaService.java` gets the
hard-failure case right (not cached), but an HTTP-200 GraphQL response with an `errors` payload
parses to an all-null row in
`src/main/java/tech/dobler/where2stream/titlecatalog/adapter/out/imdb/ImdbTitleSource.java`
that **is** negative-cached.
[ADR-0012](docs/adr/0012-permanent-title-cache-vs-ttl-availability-cache.md)'s own rationale for
the negative TTL ("a miss is more often a temporary problem") argues for retry-next-request on
failure instead.
Details: F15 in
[`docs/reviews/2026-09-10-architecture-review.md`](docs/reviews/2026-09-10-architecture-review.md).

- **Acceptance:** a source failure (transport, HTTP status, or GraphQL `errors`) is never stored
  as a negative-cache row; only a confirmed "there is no poster/metadata" is; a test per source
  pins it.

### 🟠 TODO-76 — The navbar IMDb search dies permanently after one failed request
`src/main/frontend/src/app/shared/imdb-search-box/imdb-search-box.ts`: the HTTP call sits inside
`switchMap` with no `catchError` (no API class in `src/main/frontend/src/app/core/api/` has one),
so one HTTP error completes the outer subscription — the results freeze and every subsequent
keystroke does nothing until a full page reload, with no message to the user.
Needs `catchError` **inside** the `switchMap` (per request, so the stream survives), ideally with
user feedback.
Details: F21 in
[`docs/reviews/2026-09-10-architecture-review.md`](docs/reviews/2026-09-10-architecture-review.md).

- **Acceptance:** a failing search request leaves the box functional for the next keystroke, and a
  test simulating an HTTP error proves it.

### 🟠 TODO-77 — `query_meta` generations accumulate forever; every page view loads all of them
Every re-scrape inserts a new `QueryMeta` row plus its eager `query_result`/availability children
(`src/main/java/tech/dobler/where2stream/streamingavailability/application/StreamInfoService.java`);
nothing ever deletes old generations (no delete method on
`src/main/java/tech/dobler/where2stream/streamingavailability/port/out/QueryMetaRepository.java`,
no pruning job).
`findByImdbIdIn` — executed on **every** library page view — loads every historical generation
eagerly and discards all but the newest per title: read-path cost grows linearly with instance age
(~13 generations per title per year at the 28-day TTL).
[ADR-0012](docs/adr/0012-permanent-title-cache-vs-ttl-availability-cache.md) accepts unbounded
growth for posters (disk only) but is silent on this one, which sits on the per-request path.
To decide: prune superseded generations on write, keep-latest-per-title queries, or both —
the decision belongs in an ADR-0012 extension.
Details: F11 in
[`docs/reviews/2026-09-10-architecture-review.md`](docs/reviews/2026-09-10-architecture-review.md).

- **Acceptance:** page-view queries no longer load superseded generations, and the growth story
  (prune or bounded history) is decided and documented.

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

### 🟡 TODO-78 — Serving a poster thumbnail loads the full-size BLOB too
`src/main/java/tech/dobler/where2stream/titlecatalog/domain/TitlePoster.java` maps both sizes as
materialized `@Lob byte[]`; `readCached` in
`src/main/java/tech/dobler/where2stream/titlecatalog/application/PosterService.java` fetches the
whole entity — so every thumbnail request (the grid's dominant request type) drags up to a 16 MB
MEDIUMBLOB through the driver to read the small one, and `warmPosterThumbnails` multiplies that
across the watchlist union.
Not verified with SQL logging (eager `@Lob` loading follows from standard Hibernate behaviour with
no bytecode enhancement configured) — verify first, per the `probe` skill.
Fix shape: a Spring Data projection per size, or one row per size.
Details: F12 in
[`docs/reviews/2026-09-10-architecture-review.md`](docs/reviews/2026-09-10-architecture-review.md).

- **Acceptance:** verified (SQL log or probe) that a thumbnail request no longer transfers the
  full-size column.

### 🟡 TODO-79 — `shared/platform` hosts single-context classes; ADR-0014 and ADR-0019 contradict each other
Three residents of `shared/platform` are used by exactly one context and have a natural owner —
the criterion [ADR-0019](docs/adr/0019-port-spi-for-inverted-context-dependencies.md) itself gives
for *not* living in `shared`:

| Class | Only user | Natural owner |
| --- | --- | --- |
| `src/main/java/tech/dobler/where2stream/shared/platform/outbound/HttpClientFactory.java` (+ `RealHttpClientFactory`, `OutboundHttpClients`) | titlecatalog's four sources | `titlecatalog/adapter/out` |
| `src/main/java/tech/dobler/where2stream/shared/platform/concurrency/RefreshInFlightTracker.java` | streamingavailability | that context's application layer |
| the `cacheRefreshExecutor` bean in `src/main/java/tech/dobler/where2stream/shared/platform/concurrency/AsyncConfig.java` | streamingavailability (its sizing comment encodes werstreamt.es knowledge) | ditto |

On `HttpClientFactory` the ADRs actively disagree:
[ADR-0014](docs/adr/0014-backend-by-bounded-context-and-ports-adapters.md) says it "turned out to
be Title-Catalog-internal", [ADR-0019](docs/adr/0019-port-spi-for-inverted-context-dependencies.md)
lists it as a legitimate `shared` resident.
Its javadoc's claim that callers span contexts is false either way.
Moving the classes settles the contradiction; the losing ADR gets an update note.
Details: F7 in
[`docs/reviews/2026-09-10-architecture-review.md`](docs/reviews/2026-09-10-architecture-review.md).

- **Acceptance:** the three residents live in their owning context (or an ADR update documents why
  not), the ADR-0014/0019 contradiction is resolved in writing, and `ArchitectureTest` still passes.

### 🟡 TODO-80 — The SecurityContext ArchUnit rule no longer covers the packages it targets
`security_context_is_only_read_in_the_presentation_layer` in
`src/test/java/tech/dobler/where2stream/architecture/ArchitectureTest.java` restricts
`..application..`, `..persistence..` and `..domain..` — but `persistence` packages were dissolved
by [ADR-0014](docs/adr/0014-backend-by-bounded-context-and-ports-adapters.md); persistence now
lives in `adapter.out.persistence`/`port.out`, which the pattern does not match, so a class in
`adapter.out` could read the SecurityContext today without failing any rule
(against [ADR-0006](docs/adr/0006-authentication-and-authorisation.md)/ADR-0007's intent).
While in there: `StreamInfoService` imports the adapter classes `WerStreamtProperties` and
`QueryResultMapper` directly — the same no-ceremony shortcut ADR-0014's update note blesses for
the two IMDb sources, but undocumented for these two.
Either extend the ADR-0014 blessing explicitly or route them behind the context's ports.
Details: F2/F3 in
[`docs/reviews/2026-09-10-architecture-review.md`](docs/reviews/2026-09-10-architecture-review.md).

- **Acceptance:** the rule covers all non-presentation packages (a probe violation in
  `adapter.out` turns it red), and the two undocumented imports are either ADR-documented or
  removed.

---

## 🟢 Low

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

### 🟢 TODO-81 — "Clear entire watchlist" runs without confirmation
The most destructive action in the app fires directly
(`onClear` in `src/main/frontend/src/app/features/watchlist-import/watchlist-import-page.ts`),
while the milder "remove watched" on the same page uses the shared `ConfirmDialog`
(`src/main/frontend/src/app/shared/confirm-dialog/confirm-dialog.ts`) and
`src/main/frontend/src/app/features/admin-users/admin-users-page.ts` uses native
`window.prompt`/`window.confirm` — three conventions for one interaction class.
Details: F22 in
[`docs/reviews/2026-09-10-architecture-review.md`](docs/reviews/2026-09-10-architecture-review.md).

- **Acceptance:** clearing the watchlist asks first, and destructive/confirm interactions use one
  shared mechanism (`ConfirmDialog`).

### 🟢 TODO-82 — Hardcoded English user-facing strings bypass Transloco
Three spots never enter the (parity-tested) translation catalogues:
the seen-toggle snackbars and their Undo action in
`src/main/frontend/src/app/core/seen-store.ts`;
the `'OK'` snackbar action in
`src/main/frontend/src/app/features/manage/manage-page.ts` and
`src/main/frontend/src/app/features/watchlist-import/watchlist-import-page.ts`
(settings already translates its action as `common.dismiss`);
and the route titles in `src/main/frontend/src/app/app.routes.ts`.
A German user sees English snackbars for the seen toggle — the only untranslated user-visible
strings the review found.
Details: F23 in
[`docs/reviews/2026-09-10-architecture-review.md`](docs/reviews/2026-09-10-architecture-review.md).

- **Acceptance:** the named strings come from the de/en catalogues (route-title localization may be
  deliberately declined — then documented where the titles are defined), and `i18n-parity` still
  passes.

### 🟢 TODO-83 — Small clean-up finds from the 2026-09-10 architecture review
Collected from
[`docs/reviews/2026-09-10-architecture-review.md`](docs/reviews/2026-09-10-architecture-review.md)
(F-numbers there); none is worth its own ticket:

- **Decide the werstreamt.es rate limit** (F18): `src/main/resources/application.properties` sets 20 req/s while the
  property default, the executor-sizing comment in
  `src/main/java/tech/dobler/where2stream/shared/platform/concurrency/AsyncConfig.java` and
  [ADR-0016](docs/adr/0016-asynchronous-deferred-cache-refresh.md) all reason from 2 req/s —
  either stand by 20 and update the reasoning, or lower the config.
- **Stale Javadoc in `ArchitectureTest`** (F5): the streamingavailability rule still claims the
  context "publishes no inbound port at all" — false since `AvailabilityMetricsPort`.
- **`QueryResultRepository` has no production caller** (F13), and `ix_query_result_imdb_id`
  (changeset `src/main/resources/db/changelog/changes/014-index-query-cache-imdb-id.xml`) supports
  only its tests — drop or justify.
- **Changeset `src/main/resources/db/changelog/changes/015-query-meta-due-for-refresh-at.xml`**
  uses bare `TIMESTAMP` instead of the file's own `${timestamp.type}` convention (F13).
- **MariaDB test parity** (F13): `AppUser` and `TitleMeta` repositories are H2-tested only; the
  historical MariaDB-only type bugs argue for adding them to the shared-container suite.
- **ADR-0010 idiom** (F25): `resolveAll` in
  `src/main/java/tech/dobler/where2stream/streamingavailability/application/StreamInfoService.java`
  uses guarded `isEmpty()`-then-`get()` — safe, but the literal shape the ADR forbids.
- **README smart/dumb claim** (F24): stated as absolute, contradicted by three deliberate
  exceptions (`src/main/frontend/src/app/shared/imdb-search-box/imdb-search-box.ts`,
  `src/main/frontend/src/app/shared/impersonation-banner/impersonation-banner.ts`, the
  `injectTitleMeta` consumers) — add the footnote, and record the "shared components may read
  `UserPrefsStore`" de-facto convention.
- **`platform/api` vs `platform/web`** (F9): two `@RestController`s in `web`, two in `api`, no
  discernible line — pick one and say which.

- **Acceptance:** each bullet either done or explicitly declined with the reason recorded where the
  respective code/doc lives.
