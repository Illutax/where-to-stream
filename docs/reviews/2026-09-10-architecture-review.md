# Architecture Review — where-to-stream (w2s)

> **Snapshot of 2026-09-10. Not maintained.**
> A dated snapshot is allowed to age; an undated "this is the architecture" is not
> (see the header of the [2026-07-28 predecessor](2026-07-28-architecture-review.md) for how that went).

Date: 2026-09-10.
Commit: `5688966` (branch `claude/w2s`, off `dev` @ `fc0c5fba82d0`).
Scope: the six areas of the `architecture-review` skill —
context boundaries, `shared`, persistence, outbound adapters, frontend, and the reconciliation of all 21 ADRs against the code.
Method: six parallel read-only reviewers, one per area; every claim below cites `file:line` as found in this checkout.
Tree state before the review: green — backend `mvn test` 458 tests passing (Testcontainers included), frontend `ng lint` clean and 260 tests passing.

**Explicitly not examined:**
runtime behaviour beyond the existing test suite (no live scrapes, no browser reproduction, no `EXPLAIN` plans);
the OIDC login flow end to end;
multi-instance deployment concerns (the in-memory `RateLimiter` and `RefreshInFlightTracker` are per-JVM — assumed single-instance per the README);
production data volumes;
CI outside this repository (no pipeline config is visible in this checkout);
and test-code hygiene across context boundaries (ArchUnit deliberately excludes tests).

Per the review rules, this document holds findings and reasoning only.
Every finding that needs action references the ticket or ADR it produced;
already-tracked defects are cross-referenced, not restated.

---

## Verdict in three sentences

The bounded-context architecture **holds**: every cross-context dependency goes through a published port, the ArchUnit rules genuinely cover the boundary, and all 20 accepted ADRs describe reality — the 2026-09-09 reconciliation (TODO-67) was real, not cosmetic.
The weak flank is **behaviour under failure**: both caches persist failure results as data (F14, F15), the background-refresh machinery leaks permanently under a burst (F16), and the scraper has no timeout while sitting on the user's request thread (F17).
Everything else is accumulation-grade: single-context classes pooling in `shared/platform` (F7), cache generations pooling in `query_meta` (F11), and small documentation drift.

---

## Context boundaries

**F1 — The four contexts hold, and the enforcement is genuine.** *(confirms ADR-0014, ADR-0019)*
Every cross-context import is a published contract:
`CurrentUserPort` (e.g. `src/main/java/tech/dobler/where2stream/watchlist/application/WatchlistImportService.java:7`),
`WatchlistCatalogPort`, `TitleCacheMaintenancePort`, the four metrics ports, the `PosterAttributionProvider` SPI
(`src/main/java/tech/dobler/where2stream/titlecatalog/adapter/out/tmdb/TmdbProperties.java:27`),
plus the two class-pinned watchlist read-model types the rule exempts by name.
`accountaccess` imports nothing from any other context; the dependency graph is acyclic.
`ArchitectureTest` covers it with one isolation rule per context, a cross-context cycle rule, and **no** freeze files or ignore patterns —
every allowance is inline, named, and justified
(`src/test/java/tech/dobler/where2stream/architecture/ArchitectureTest.java:114-185,202-207`).

**F2 — The SecurityContext rule no longer covers the packages it was written for.** *(gap vs. ADR-0007's intent)* → TODO-80
`ArchitectureTest.java:87` restricts `SecurityContextHolder` reads out of `..application..`, `..persistence..` and `..domain..` —
but `persistence` packages were dissolved by ADR-0014; persistence now lives in `adapter.out.persistence`/`port.out`, which the pattern does not match.
A class in `adapter.out` could read the SecurityContext today without failing any rule.

**F3 — Two application→adapter imports lack the ADR-0014 blessing.** *(gap)* → TODO-80
ADR-0014's update note deliberately blesses the application layer using `ImdbTitleSource`/`ImdbSuggestionSource` without a port.
`StreamInfoService` does the same with `WerStreamtProperties` and `QueryResultMapper`
(`src/main/java/tech/dobler/where2stream/streamingavailability/application/StreamInfoService.java:8,14`) — same spirit, but undocumented,
and `QueryResultMapper` living in `adapter.out.persistence` while called from the application service inverts the ports-and-adapters direction.
Nothing enforces intra-context layering at all (no rule forbids `application → adapter`), so the pattern can spread silently.

**F4 — No intra-context layering or domain-purity rules; domain is JPA-bound.** *(observation, consistent with ADR-0014's framing A)*
`domain` classes import `jakarta.persistence` throughout (e.g. `src/main/java/tech/dobler/where2stream/watchlist/domain/WatchlistEntry.java:3`).
That is the pragmatic framing ADR-0014 chose, recorded here so the next reader does not mistake "domain" for "framework-free".
No ticket — this is the documented decision working as designed.

**F5 — Stale prose around live rules.** *(doc drift)* → TODO-83
ADR-0014 still describes the ArchUnit exemption as all of `shared..`; it was narrowed to `ApiExceptionHandler` alone (`ArchitectureTest.java:59-60`).
The streamingavailability rule's Javadoc claims the context "publishes no inbound port at all" (`ArchitectureTest.java:169-175`) —
false since `AvailabilityMetricsPort`.
`port.spi` is exempted only in the accountaccess rule (`ArchitectureTest.java:121`); adding an SPI to another context would need a rule edit (latent, arguably a feature).

## `shared`

**F6 — `shared/kernel` is exactly what it claims to be.** *(confirms ADR-0009, ADR-0014)*
Two value types (`ImdbId`, `ReleaseYear`) plus their JPA/MVC adapters, used by three of the four contexts, no context imports.
The one overstatement: CLAUDE.md says "value types every context needs" — `accountaccess` uses neither.

**F7 — `shared/platform` has accumulated single-context residents — early junk-drawer accretion, including a live ADR contradiction.** *(contradicts ADR-0014; exposes an ADR-0019 gap)* → TODO-79
`HttpClientFactory`, `RealHttpClientFactory` and `OutboundHttpClients` (`src/main/java/tech/dobler/where2stream/shared/platform/outbound/`)
are used **only** by titlecatalog's four outbound adapters, yet `HttpClientFactory.java:14-16` claims its callers span contexts.
ADR-0014 itself says "`HttpClientFactory` turned out to be Title-Catalog-internal" while ADR-0019 lists it as a legitimate `shared` resident — the two ADRs contradict each other, and the code sides with neither cleanly.
`RefreshInFlightTracker` (`src/main/java/tech/dobler/where2stream/shared/platform/concurrency/RefreshInFlightTracker.java`) and the
`cacheRefreshExecutor` bean (`src/main/java/tech/dobler/where2stream/shared/platform/concurrency/AsyncConfig.java:27-36`, whose sizing comment encodes werstreamt.es knowledge)
serve only streamingavailability.
All three have a natural owner, which is exactly ADR-0019's own criterion for *not* living in `shared`.
Genuinely multi-context residents check out: `TimeService` (all four), `RateLimiter` (two), `ValidationException` (three).

**F8 — ADR-0003 (time facade) holds on both sides, with known edges.** *(confirms ADR-0003)*
Backend: only `SystemTimeService` calls `now()`, enforced by ArchUnit (`ArchitectureTest.java:68-76`).
Frontend: ESLint `no-restricted-syntax` with the same shape (`src/main/frontend/eslint.config.js:36-53`), zero violations.
Edges, recorded not ticketed: the rule does not ban `ZonedDateTime.now`/`OffsetDateTime.now` (currently unused),
and `System.nanoTime` in `RateLimiter`/`ExecutionTimeAspect` is monotonic elapsed-time measurement outside the facade — in the ADR's spirit, but not clock-substitutable in tests.

**F9 — The `platform/api` vs `platform/web` split is inconsistent.** *(minor gap)* → TODO-83
`StatusApiController`/`AdminMetricsApiController` sit in `api/` while the equally-REST `StatusController` and the services/DTOs sit in `web/`.
Cosmetic, but it is the "sorted technically without it being apparent why" smell the kernel/platform split was meant to cure.

## Persistence

**F10 — No schema drift; the persistence ADRs hold and are tested.** *(confirms ADR-0011, ADR-0012, ADR-0018)*
Field-by-field comparison of all six entities against all 18 changesets found no mismatch.
No `FetchType.LAZY` exists; OSIV is off in main and test config; `ddl-auto=none` in both.
Every production `save()` is a transient-entity insert or a commented dual-path exception, exactly as ADR-0018 rules 2/5 require —
and `DirtyCheckingPersistenceTest` pins the convention with a real commit.
Hot paths are indexed (`014-index-query-cache-imdb-id.xml`, the watchlist unique constraint, unique `imdb_id` columns).

**F11 — `query_meta` generations accumulate forever, and the read path pays for all of them on every page view.** *(gap; the clearest model-vs-usage drift)* → TODO-77
Every re-scrape inserts a new `QueryMeta` row plus its eager `query_result`/availability children
(`src/main/java/tech/dobler/where2stream/streamingavailability/application/StreamInfoService.java:192`); nothing ever deletes old generations
(no delete method on `QueryMetaRepository`, no pruning job).
`findByImdbIdIn` — executed on every library page view — loads every historical generation eagerly and discards all but the newest per title
(`StreamInfoService.java:105-110`).
With the 28-day TTL that is ~13 generations per title per year of linear read-path growth.
ADR-0012 accepts unbounded growth for posters (disk only); it is silent on this one, which sits on the per-request path.

**F12 — Serving a poster thumbnail loads the full-size BLOB too.** *(gap)* → TODO-78
`TitlePoster` maps both sizes as materialized `@Lob byte[]` (`src/main/java/tech/dobler/where2stream/titlecatalog/domain/TitlePoster.java:47-56`);
`PosterService.readCached` fetches the whole entity (`src/main/java/tech/dobler/where2stream/titlecatalog/application/PosterService.java:120-125`),
so every thumbnail request — the grid's dominant request type — drags up to a 16 MB MEDIUMBLOB through the driver to read the small one.
The entity's own Javadoc says the sizes are requested independently; the model no longer matches that usage.
Not verified with SQL logging (no bytecode enhancement is configured, so eager `@Lob` loading follows from standard Hibernate behaviour).

**F13 — Small persistence finds.** *(minor)* → TODO-83
`AppUser` and `TitleMeta` have no MariaDB repository tests (H2 only) — the historical MariaDB-only type bugs (changesets 004, 008) argue for parity.
`QueryResultRepository` has no production caller, and `ix_query_result_imdb_id` (changeset 014) therefore supports nothing but its tests — pure write overhead on the busiest insert path, with a changelog comment whose justification no longer matches usage.
Changeset `015-query-meta-due-for-refresh-at.xml:14` uses bare `TIMESTAMP` instead of the file's own `${timestamp.type}` convention (second precision, 2038 range on MariaDB — harmless today, drift nonetheless).

## Outbound adapters

**F14 — Cache poisoning: a failed scrape is persisted as fresh "available nowhere" for 28 days, overwriting good data.** *(contradicts the spirit of ADR-0016/0012; the most important finding of this review)* → TODO-72
`WerStreamtEsSource.query` maps any `HttpStatusException` (404, 429, 503, …) to an empty list
(`src/main/java/tech/dobler/where2stream/streamingavailability/adapter/out/werstreamtes/WerStreamtEsSource.java:88-94`),
and a site-wide markup change makes `parse` return the same empty list with zero log signal if the `#avalibility > .provider` selector stops matching (`:98-102`).
`StreamInfoService.fetch` then saves whatever came back — including empty — as a fresh, non-invalidated row with a new `creationTime`
(`StreamInfoService.java:186-194`), which `resolve` prefers as the newest generation.
"Scrape failed", "markup changed" and "genuinely available nowhere" are persisted identically; there is no availability-side error/negative distinction at all.
The failure mode is asymmetric: a *transport* failure throws `ScrapingException` and correctly leaves the old row in place; a *status-code* failure poisons.
ADR-0012 and ADR-0016 are silent on failure semantics — this is an ADR gap as much as a code gap.

**F15 — Same class of problem on the titlecatalog side: an outage is negative-cached for 14 days.** *(contradicts ADR-0012's stated rationale)* → TODO-75
`PosterService.discover` stores `findPosterPath(...).orElse(null)` unconditionally (`PosterService.java:109-113`), collapsing "fetch failed" into "title has no poster" —
`classify` then honours it as a fresh negative for `poster.negative-cache-days`.
`TitleMetaService` correctly refuses to cache a hard fetch failure (`src/main/java/tech/dobler/where2stream/titlecatalog/application/TitleMetaService.java:63`),
but an HTTP-200 GraphQL response with an `errors` payload parses to an all-null row that **is** negative-cached
(`src/main/java/tech/dobler/where2stream/titlecatalog/adapter/out/imdb/ImdbTitleSource.java:104-114`, `TitleMetaService.java:79-86`).

**F16 — A background-refresh burst permanently leaks the in-flight tracker.** *(gap)* → TODO-73
The executor is 2 threads with a 200-deep queue and the default abort policy (`AsyncConfig.java:27-36`; no `RejectedExecutionHandler` anywhere).
`BackgroundCacheRefreshService.refreshDueEntries` marks **all** due ids in-flight first, then submits one by one
(`src/main/java/tech/dobler/where2stream/streamingavailability/application/BackgroundCacheRefreshService.java:64-65`);
submission #203 throws `TaskRejectedException`, aborting the loop — every already-marked id whose task never ran stays in `RefreshInFlightTracker` **forever** (no expiry),
blocking demand-driven and scheduled refreshes of those titles until restart.
Bulk invalidation from *Manage cache* makes all invalidated titles due at once and bypasses the jitter, so >202 due titles is realistic.
The demand-driven path has the same tryStart-then-submit-without-cleanup shape (`StreamInfoService.java:138-142`).

**F17 — The scraper has no explicit timeout, and one failing title answers 502 for a whole page.** *(gap; partly adjacent to TODO-66)* → TODO-74
The jsoup connection sets user-agent and referrer only — no `.timeout(...)` anywhere under `streamingavailability/`
(`src/main/java/tech/dobler/where2stream/streamingavailability/adapter/out/werstreamtes/ApiClientUtils.java:12-14`) — so the library default applies, on the user's request thread:
never-cached titles are fetched synchronously via `misses.parallelStream()` on the shared `ForkJoinPool.commonPool` (`StreamInfoService.java:124-126`).
A single `ScrapingException` escapes the collector and turns the **entire** dashboard/provider response into a 502, cached titles and all;
a fresh import of 500 titles blocks the first page view for ≥25 s while saturating the common pool app-wide.
`RefreshService.refresh` (`src/main/java/tech/dobler/where2stream/streamingavailability/application/RefreshService.java:42-48`) has the same all-or-nothing shape, non-resumable.
The JSON adapters, by contrast, are clean: 5 s connect / 10 s request timeouts and degrade-to-empty on every path
(`src/main/java/tech/dobler/where2stream/shared/platform/outbound/OutboundHttpClients.java:28-34`, all four titlecatalog sources).
Circuit breaking for all of this is already tracked as TODO-66; timeout and failure isolation are prerequisites, not duplicates.

**F18 — The werstreamt.es rate limit runs at 10x the value every piece of reasoning assumed.** *(config vs. documented rationale)* → TODO-83 (decide)
`application.properties:33` sets 20 req/s; the property default, the executor-sizing comment (`AsyncConfig.java:23-26`) and ADR-0016 all reason from 2 req/s.
The mechanism works at either value; the politeness stance and the pool sizing rationale are what drifted.
Related minor: `ImdbTitleSource` and `ImdbPosterSource` each build their own limiter from the same `imdb-poster.rate-limit.requests-per-second` — one configured budget, two enforced ones (different hosts, so defensible, but the Javadoc suggests one throttle).

**F19 — The refresh machinery otherwise does what ADR-0016 promises.** *(confirms ADR-0016)*
Stale entries are served immediately and marked; only never-cached titles wait;
the persisted per-row jitter (`due_for_refresh_at`, `StreamInfoService.java:199-206`, changeset 015) works as designed including the legacy-null fallback;
the tracker deduplicates the two async paths.
The synchronous paths (miss, admin force-refresh, `PreCacheService`) do not consult the tracker — worst case is a wasted duplicate scrape and a redundant generation row (minor, recorded not ticketed).

## Frontend

**F20 — The frontend architecture is consistent and its ADRs hold.** *(confirms ADR-0001, ADR-0004, ADR-0013)*
Hash routing and relative API base as decided; all 8 routes lazy-load; the 1 MB budget trigger stands (~658 kB raw initial — TODO-52's stance holds).
State is a coherent hybrid: root signal stores for cross-cutting state, a uniform thin RxJS API layer, the identical data/loading/error signal trio per feature page.
Exactly two production `effect()`s exist and both are the sanctioned ongoing-sync cases ADR-0013 names; the one-shot bootstrap is a plain subscription as the ADR prescribes.
No feature imports another feature; skeleton loading and `ErrorAlert` error handling are uniform.

**F21 — The navbar IMDb search dies permanently after one failed request.** *(gap; clearest frontend defect)* → TODO-76
`src/main/frontend/src/app/shared/imdb-search-box/imdb-search-box.ts:122-133`: the HTTP call sits inside `switchMap` with no `catchError`
(none exists anywhere in `src/main/frontend/src/app/core/api/`), so one error completes the outer subscription —
the results freeze and every subsequent keystroke does nothing until a full page reload, with no message to the user.

**F22 — The most destructive action in the app runs without confirmation.** *(gap)* → TODO-81
"Clear entire watchlist" fires directly (`src/main/frontend/src/app/features/watchlist-import/watchlist-import-page.ts:211`),
while the milder "remove watched" on the same page uses `ConfirmDialog`, and `admin-users` uses native `window.prompt`/`window.confirm`
(`src/main/frontend/src/app/features/admin-users/admin-users-page.ts:170,181`) — three conventions for one interaction class.

**F23 — Hardcoded English bypasses Transloco in three places.** *(gap)* → TODO-82
Seen-toggle snackbars and their Undo action (`src/main/frontend/src/app/core/seen-store.ts:39-47`),
the `'OK'` snackbar action in manage/import pages (vs. the translated `common.dismiss` in settings),
and the route titles in `src/main/frontend/src/app/app.routes.ts`.
The de/en parity of the catalogues themselves is test-guarded (`src/main/frontend/src/i18n/i18n-parity.spec.ts`) — these strings just never enter them.

**F24 — The README's smart/dumb claim is stated as absolute and is not.** *(doc gap; the code's deviations are deliberate)* → TODO-83
Three `shared/` components own HTTP (`imdb-search-box` — self-documented as "the only smart piece", `impersonation-banner`, and the `injectTitleMeta` consumers),
and six shared components inject `UserPrefsStore` directly — a consistent de-facto convention nothing records.
The one architectural rule the README states is exactly the one ESLint does not check (no import-boundary rule).
Related silent-failure observations (background/bootstrap calls fail quietly by design: navbar count, prefs persistence, `/api/me` on 500) are recorded here, judged defensible, not ticketed.
The per-row metadata requests and missing cancellation remain tracked as TODO-59.

## ADR reconciliation

**F25 — All 20 accepted ADRs hold; ADR-0017 is correctly superseded and verifiably dismantled; none is quietly broken.** *(confirms the whole index)*
Hostile spot-checks of the discipline-only ADRs came back clean:
zero raw JUnit/Hamcrest assertions (0005), zero `String imdbId` parameters (0009),
all six `orElse(null)` occurrences within 0010's explicit delimitation,
every `save()` justified (0018), commands self-validating with the ArchUnit rule 0015's correction promised.
The one literal-idiom slip: `StreamInfoService.resolveAll` uses guarded `isEmpty()`-then-`get()` (`StreamInfoService.java:110-114`) — safe, but the shape ADR-0010 forbids. → TODO-83
Stale details corrected in this pass (update notes, no decision changes): ADR-0002 still quoted the pre-0021 `engines` range; ADR-0014's enumeration of `shared/platform` predated `concurrency/` and `observability/` and still described the old blanket `shared` exemption.
The ADR-0019/0014 contradiction over `HttpClientFactory` is left to the move decision. → TODO-79

**F26 — Enforcement asymmetry worth knowing.** *(observation)*
Backend rules (0003, 0011-corollary, 0014, 0015, 0019) run on every `mvn test` via ArchUnit.
The frontend ESLint rule (0003) is not wired into the Maven build, and no CI config is visible in this repo — it relies on the documented `ng lint` workflow actually being run.
ADR-0021's own caveat stands: nothing will flag the Node 26 LTS transition (2026-10-28, ~7 weeks out).
