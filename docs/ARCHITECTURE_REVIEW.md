# Architecture Review — where-to-stream (w2s)

Date: 2026-07-28.
Scope: whole module (backend + Angular frontend).
Architecture-level review across five dimensions (backend structure/duplication, database/persistence, frontend consistency, security/operational posture, test coverage/code health)
— not a full line-by-line audit.
Concrete, already-tracked defects live in [`TODOs.md`](../TODOs.md);
this review adds new findings and cross-references old ones instead of duplicating them.

The 2026-06-28 review that used to live in this file is now superseded
— every finding in it (`entities/` naming, `ImdbEntryRepository`, the provider-handler duplication, the controller transaction boundary, `invalidated`, the scraper abstraction, `getAll()`) was resolved via TODO-30 through TODO-37,
and the Thymeleaf-era classes it discussed (`DataAggregateController`, `ImdbEntryRepository`, `AggregateService`) no longer exist.
See the bottom of this file for a one-paragraph pointer instead of the full obsolete content.

## What the system is now

Backend layering is `api/` → `application/` → `services/` → `persistence/`,
over a `domain/` leaf (`configurations/`, `time/` cross-cutting),
enforced by ArchUnit (`ArchitectureTest.java`).
The Thymeleaf UI is gone (ADR-0008);
the Angular SPA is the only UI, talking to a JSON API.
The watchlist is per-user and DB-backed (ADR-0007).
Domain concepts are value objects (ADR-0009).
Schema is Liquibase-only, 13 changesets deep.
Four independent outbound HTTP integrations now exist: werstreamt.es scraping, IMDb GraphQL (poster+meta), TMDB (optional poster source),
and — newest — IMDb's suggestion/typeahead API for the navbar search.

## ⚠️ Two self-introduced issues from the most recent work (highest priority)

These aren't legacy debt
— they're gaps in the IMDb-search feature added earlier this session,
worth fixing before anything else below.

### F1 — `ImdbSuggestionClient.search()` can throw uncaught, breaking its own "never fails" contract 🔴
`services/ImdbSuggestionClient.java`: the request URI is built as `properties.apiUrl() + "/" + firstChar + "/" + URLEncoder.encode(trimmed, UTF_8) + ".json"`
— the query itself is correctly percent-encoded, but the lone `firstChar` path segment is spliced in raw,
and `URI.create(...)` sits **outside** the `try` block.
Verified experimentally: a query starting with `%`, `"`, `\`, or a space makes `URI.create` throw `IllegalArgumentException` ("Malformed escape pair" / "Illegal character in path"),
uncaught by the method, uncaught by `ImdbSearchService`, and unmapped by `ApiExceptionHandler`
— it reaches the client as an unfiltered 500.
The class's own Javadoc claims "failures degrade to an empty list";
this one failure mode doesn't.
A user typing `"Matrix` or a leading space (easy to do by accident) breaks search entirely until they clear it.

### F2 — `/api/imdb/search` has no per-request throttle beyond a shared blocking lock 🟠
`ImdbSearchApiController` → `ImdbSuggestionClient.search()` → `acquire()` is `synchronized`
and blocks the calling thread in `Thread.sleep` while holding that lock
(default 1 req/s, shared across every user — it's a singleton bean).
Nothing rejects an over-limit request;
it just queues the servlet thread.
The client-side debounce (1s) protects the well-behaved UI,
but nothing stops a script (or 20 browser tabs) hammering this endpoint directly, tying up request-handling threads across the whole app
— a real, if modest, DoS lever that the admin-only scrape/refresh endpoints don't expose
because those aren't triggered by every keystroke of an arbitrary authenticated user.

## High-priority findings

### F3 — Four copies of the same HTTP-client + rate-limiter, with one already-drifted behavior bug 🔴
`services/ImdbTitleClient.java`, `services/ImdbPosterSource.java`, `services/TmdbPosterSource.java`, and `services/ImdbSuggestionClient.java` each independently construct their own `java.net.http.HttpClient`
and reimplement the same `synchronized acquire()` nanosecond rate-limiter (~140 duplicated lines total)
— despite a shared `services/RateLimiter.java` `@Component` already existing and being used by `WerStreamtEsApiClient`.
This isn't just redundant: `TmdbPosterSource` already omits `.followRedirects(NORMAL)`
and never sends the `User-Agent` header the other three send on every request
— a real, silent drift that makes this duplication actively risky, not just verbose.
Worth consolidating onto the existing `RateLimiter` + one shared HTTP client builder.

### F4 — `query_meta.imdb_id` / `query_result.imdb_id` have no index 🔴
`db/changelog/changes/001-baseline-schema.xml` creates both as plain `varchar(255)`, no index/constraint.
Every cache read/write in the app filters on exactly this column (`QueryMetaRepository`, `QueryResultRepository`)
— it's the busiest table in the system,
and the one `imdb_id`-keyed table that got no index (`watchlist_entry`, `title_meta`, `title_poster` all have one).
Full table scans on every search/refresh.
Compounds TODO-12 (`FetchType.EAGER` on the same entities)
— fixing both together is more valuable than either alone.

### F5 — Admin endpoints rely solely on URL-pattern security, no method-level defense-in-depth 🟠
`ManageApiController`/`CacheManagementService` and `RefreshApiController` carry no `@PreAuthorize`, unlike `UserAdminService`,
which explicitly adds `@PreAuthorize("hasRole('ADMIN')")` on top of the URL rule.
Protection for cache invalidation, forced re-scrape, and forced refresh rests entirely on `SecurityConfig`'s path matchers.
A future rename of a `@RequestMapping` prefix, or a new admin action under a path that doesn't match the existing patterns,
silently loses ADMIN gating with nothing to catch it
— `@EnableMethodSecurity` is already on but unused here.

### F6 — Known default admin password shipped in the env template 🟠
`.env.example` sets `W2S_ADMIN_PASSWORD=change-me-please`;
`AdminUserSeeder` uses it verbatim with no length/complexity check and no comparison-against-the-known-example-value guard.
Every other `.env.example` field requires a genuinely new secret;
this one already "looks filled in," making it the easiest field to forget to change.
`UserAdminService.create()`/`resetPassword()` also enforce no minimum length/complexity on any admin-set password (only non-blank).

## Medium-priority findings

### F7 — No expiry/cleanup for positive cache rows 🟠
`title_meta`, `title_poster` (BLOBs up to 16MB per title after `008-widen-poster-blobs`), `query_meta`/`query_result` have no FK from `watchlist_entry`, no scheduled cleanup,
and no orphan-removal when a title leaves every user's watchlist.
Only the *negative* cache path has a TTL.
These accumulate forever.

### F8 — `PosterService`/`TitleMetaService` duplicate a whole non-trivial idiom across two layers
Both independently implement the same "self-proxy short-transaction
so the network call happens outside a DB transaction, swallow `DataIntegrityViolationException` on a racing insert" pattern,
split awkwardly across `application/` and `services/`.
Not simple duplication
— the kind that drifts if one gets a bugfix and the other doesn't.

### F9 — `UserPreferencesService` grows a copy-pasted getter/setter pair per preference
Six near-identical pairs now (theme, showAgeRatings, language, showGermanTitle, viewMode, tilesPerRow
— the last two added this session for the grid-view feature).
Same shape every time:
`xFor(username)` with a default fallback, `updateX(username, value)` transactional save.
Worth a generic `getPreference`/`updatePreference` or a single preferences record instead of one method pair per field,
before a seventh gets added.

### F10 — Frontend: 4-5 near-identical single-field preference stores
`theme-store.ts`, `age-rating-store.ts`, `language-store.ts`, `german-title-store.ts` (plus `grid-prefs-store.ts`) are each backed by their own single-purpose API class,
all populated from one `Me` object in one `effect()` in `app.ts`,
all injected individually into `settings-page.ts`.
Mirrors F9 on the frontend
— 8-10 files reimplementing the same `init()`/`set()` shape instead of one `UserPrefsStore` + one `PreferencesApi`.
`GridPrefsStore` already exposes a different shape (`setViewMode()`/`setTilesPerRow()` instead of one `set()`),
foreshadowing further drift as more prefs get added.

### F11 — Frontend: smart/dumb boundary violations, one pre-existing and one from this session
`shared/title-cell/title-cell.ts` and `shared/title-tile/title-tile.ts` both call `injectTitleMeta()`,
which does a raw `HttpClient.get()`
— not even routed through an `*Api` class —
directly inside components the README calls "presentational, only render inputs."
This predates the current feature wave.
Newer and narrower: `shared/add-to-watchlist-dialog/add-to-watchlist-dialog.ts` lives under `shared/`
but injects `WatchlistApi` directly and owns its own `busy`/`error` mutation state
— a real container, not a dumb component, under the wrong directory by the project's own stated convention.

### F12 — Newer controllers bypass `ApiExceptionHandler` via raw `ResponseStatusException`
Most 400-response validation in the newest controllers throws `ResponseStatusException` directly rather than a mapped application exception.
Since neither `spring.mvc.problemdetails.enabled` nor `server.error.include-message` is set,
the carefully-written reason messages may be silently dropped from the response body
— untested;
no controller test currently asserts the error body content, only the status code.

### F13 — Test suite runs with `spring.jpa.open-in-view` re-enabled, contradicting ADR-0011
`src/test/resources/application.properties` is missing the `spring.jpa.open-in-view=false`
that main `application.properties` sets.
Confirmed live: every test-context Spring Boot startup logs the OSIV warning.
The tests meant to guard this architectural decision don't actually reproduce the production configuration.

### F14 — Security-critical and load-bearing classes with 0% or near-0% test coverage
`security/GoogleOidcUserService.java` (OIDC first-login provisioning, disabled-account rejection, role mapping)
— 0% coverage, no test class.
`application/TitleInfoService.java` — 0%, no test class.
`services/RateLimiter.java` — 37.9%, the actual blocking/timing behavior looks untested (only the disabled-limit branch).
`TmdbPosterSource`/`ImdbPosterSource` — 23-25% coverage each, 2-4 test methods for 100+ line classes.

## Low-priority / worth a mention

- **DB naming leftover**: `query_result.title` actually stores the *streaming service name*, not a title (`persistence/QueryResultDB.java`)
  — same class of misleading-name bug already fixed elsewhere in the same baseline changeset (TODO-2/TODO-3), missed for this one column.
- **DB portability inconsistency**: `query_result_availabilities.type` uses a raw native `enum('BUY','RENT')` SQL type
  while every other enum in the schema is `varchar` via `@Enumerated(STRING)`
  — works today, but needs a native `ALTER TABLE` on MariaDB to extend, unlike its varchar-backed siblings.
- **DB**: `app_user.email` has no index and no unique constraint;
  two accounts could collide on case-variant emails across different OIDC providers.
- **DB**: `QueryMeta`/`QueryResultDB` rely on the default Hibernate naming strategy to resolve to snake_case
  rather than spelling table/column names out explicitly, unlike every sibling entity
  — works today, silently fragile to a future naming-strategy change.
- **DB**: `title_meta` lacks the H2-and-MariaDB-via-Testcontainers coverage its sibling tables have
  (`WatchlistEntry`, `QueryMeta`, `QueryResult`, `TitlePoster` all have an `Abstract*RepositoryTests` base + a Testcontainers subclass;
  `title_meta` is H2-only)
  — the README's "runs against a real MariaDB" claim is stale for this one table.
- **DB**: `watchlist_entry` has no composite index on `(user_id, is_rated)`
  — low severity at typical per-user list sizes,
  but `findByUserIdAndRatedTrue`/`deleteByUserIdAndRatedTrue` filter the boolean unindexed.
- **Backend**: `WatchlistImportService.resolveUserId` is a redundant one-line alias for `CurrentUserService.resolveId`
  — a small naming inconsistency introduced by the newest controller
  (should just inject `CurrentUserService` directly, as `ImdbSearchApiController` already does).
- **Backend**: `ImdbSearchService.search()`'s per-result `existsByUserIdAndImdbId` call is a real N+1,
  but bounded and low severity (`maxResults` defaults to 8).
- **Frontend**: `@angular/animations` was added purely to enable `provideAnimationsAsync()` for `MatDialog`/`mat-menu`
  — a reasonable trade for real functionality,
  but the package itself is deprecated upstream in favor of native `animate.enter`/`animate.leave`;
  tracked tech debt.
- **Frontend**: `settings-page.spec.ts` (2 tests) is thin relative to `settings-page.ts`'s actual branching logic (409-conflict vs. generic rename error, redirect-on-success)
  — no test covers any of it,
  unlike `overview-page.spec.ts`/`provider-page.spec.ts` (11 tests each, error paths included).
- **Frontend**: `shared/loading/loading.ts`, `shared/status-card/status-card.ts`, `core/unauthorized-interceptor.ts` have no `.spec.ts` at all, unlike their siblings.
  `unauthorized-interceptor.ts` is the one most worth covering
  — it hard-redirects the browser on any 401.
- **Frontend**: `de.json`'s `users.adminLabel: "admin {{ username }}"` is byte-identical to the English string
  — worth a quick check for an actual missed translation vs. an intentional cognate.
- **Backend**: `services/WatchlistCatalog.java` has no dedicated test class (only 80.9% coverage via indirect callers);
  its `toEntry(...)` mapping has no test pinning field order.

## Already tracked, independently reconfirmed here

- **TODO-20** (central error handling): reconfirmed independently by three of the five review passes (backend, security, test-health)
  — `WerStreamtEsApiClient` still throws a bare `RuntimeException` on IO failure, unmapped by `ApiExceptionHandler`, surfacing as an unfiltered 500.
  Given how many angles converged on this one,
  it's probably the single highest-value item left in `TODOs.md`.
- **TODO-12** (`FetchType.EAGER`): still open,
  and directly compounds F4 above (unindexed FK + eager collection fetch on the same entities)
  — worth doing together.
- **TODO-14** (unpinned `versions-maven-plugin`): still open, unchanged.

## Suggested order of work (not yet agreed — for discussion)

1. **F1** (uncaught exception in `ImdbSuggestionClient`)
   — a one-line fix (move `URI.create` inside the `try`, or encode `firstChar` too),
   highest risk-to-effort ratio of anything here.
2. **F2** (search endpoint throttling)
   — decide whether to reject-over-limit instead of block-and-queue, and/or add a per-user cap.
3. **F4 + TODO-12 together** (index + fetch type on `QueryMeta`/`QueryResultDB`)
   — same two files, same migration.
4. **TODO-20** (central error handling for scraping/IO) — reconfirmed from three directions.
5. **F5 + F6** (admin method-security defense-in-depth; stronger default-password handling).
6. Everything else, roughly in the order listed above, as capacity allows.

---

*Previous review (2026-06-28) superseded*:
covered the pre-Thymeleaf-removal codebase (`DataAggregateController`, `ImdbEntryRepository`, `AggregateService`, etc.).
Every finding in it was resolved via TODO-30 through TODO-37 (see `TODOs.md`);
none of the classes it discussed still exist.
Not reproduced here
— see git history for the prior content if needed.
