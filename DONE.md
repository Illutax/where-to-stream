# DONE — finished and abandoned TODOs

> **Historical. Not maintained.**
>
> Class, package and path names are as they stood **at the time the entry was closed**.
> The move to bounded contexts ([ADR-0014](docs/adr/0014-backend-by-bounded-context-and-ports-adapters.md))
> replaced `services/`, `persistence/`, `web/`, `rest/` and `entities/` as top-level packages;
> many entries here still point at those. That is **not an error, it is expected** —
> a ticket describes the world it was written in.
>
> For how things stand today: [`CLAUDE.md`](CLAUDE.md) and [`docs/adr/`](docs/adr/).
> For open work: [`TODOs.md`](TODOs.md).
>
> This is why `DocumentationConsistencyTest` runs over `TODOs.md` but **not** over this file.

Status: ✅ done · ❌ abandoned

---

## Bugs / Correctness

---

### ✅ TODO-1 — `ImdbApiClient.search()` is broken / unused
`services/ImdbApiClient.java`: loads the document with `connect.get()`
and then hard-codes `return null;`.
The class is also not a Spring bean (no `@Service`) and is not used anywhere in production.
- **Acceptance criterion:** Either implement parsing of the IMDb list page properly
  (return `List<SearchResult>` instead of `null`) **or** delete the class along with its test.
- **Note:** The existing `ImdbApiClientTest` makes a real network call against imdb.com without any assertion
  and fails in the sandbox at the egress proxy (403).
  Clean it up along the way (replace it with a test against a stored HTML fixture).

---

### ✅ TODO-2 — Table/column typos
- `persistence/QueryMeta.java`: `@Table(name = "QeryMeta")` → `QueryMeta`.
- `persistence/QueryResultDB.java`: `query_result_availablilities` → `query_result_availabilities`.
- **Careful:** A schema migration is required (see TODO-10), `ddl-auto=update` does not rename tables automatically
  → existing data would otherwise be lost.
- **Done:** Entity annotations corrected;
  the corrected schema is part of the Liquibase baseline (TODO-27).

---

### ✅ TODO-3 — Misleading join column name
`persistence/QueryResultDB.java`: `@CollectionTable(joinColumns = @JoinColumn(name = "imdb_id"))`
actually joins on the UUID primary key of `QueryResultDB`, not on an IMDb ID.
- **Acceptance criterion:** Rename the column to something like `query_result_id` (with a migration).
- **Done:** The join column is now called `query_result_id`; schema via Liquibase (TODO-27).

---

---

## Security

---

### ✅ TODO-5 — State-changing endpoints as GET without auth
`/pre-cache`, `/check-pre-cache`, `/refresh/all`, `/refresh/seen` triggered expensive remote crawls,
were reachable via GET and could therefore be triggered by crawlers/prefetch.
- **Acceptance criterion:** Switch to `POST`;
  put the endpoints behind authentication (add Spring Security — the app was wide open).
- **Done (auth):** Spring Security added ([ADR-0006](docs/adr/0006-authentication-and-authorisation.md));
  the new REST API uses the correct verbs (`POST /api/refresh`, `POST /api/cache`, …).
- **Done (verbs):** Removing the Thymeleaf client
  ([ADR-0008](docs/adr/0008-remove-the-thymeleaf-client.md)) deleted the legacy GET endpoints
  (`/pre-cache`, `/check-pre-cache`, `/refresh/**`) — there are no more mutating GETs **without auth** (writing read-through caches such as `/api/search` and `/api/titles/{id}/meta` still exist — they sit behind `authenticated()`);
  maintenance runs exclusively through `POST /api/**` (ADMIN).

---

---

## Architecture / Design

---

### ✅ TODO-6 — Controller calls controller
`web/ChangeListController.java` injected `rest/PreCacheController` and called
`cacheController.cache()`.
- **Acceptance criterion:** Extract the cache logic into a `PreCacheService` used by
  both controllers.
- **Done:** Introduced `PreCacheService.cacheAll()` / `findUncached()`;
  `PreCacheController` and `ChangeListController` use the service.

---

### ✅ TODO-7 — Configuration scattered across `@Value`
`wer-streamt.path` was injected separately in `ExportReader` and `FileUtils`,
`wer-streamt.invalidate.after-days` in `StreamInfoService`.
- **Acceptance criterion:** Bundle them into a `@ConfigurationProperties` record `WerStreamtProperties`.
  That also gets rid of the fragile `@Value` field injection in `FileUtils`
  (which is created via `new FileUtils()` in `JpaConfig`).
- **Done:** `WerStreamtProperties` (with `Invalidate.afterDays`, default 28) enabled via
  `@ConfigurationPropertiesScan`. `FileUtils` is now a `@Component` with
  constructor injection; `JpaConfig` injects it instead of building `new FileUtils()`.

---

### ✅ TODO-8 — `ImdbEntryRepository` is not thread-safe
`services/ImdbEntryRepository.java`: in-memory store backed by a `HashMap`, but it was
repopulated via `clear()`/`init()` from `ChangeListController` while `parallelStream` requests
were still running → potential race.
- **Acceptance criterion:** `ConcurrentHashMap` + atomic swap of the maps on reload,
  or synchronize the reload.
- **Done:** The entire state (both maps + list name) lives as an immutable `State` record
  behind an `AtomicReference`; `init`/`clear` swap the snapshot atomically, reads are
  lock-free and consistent.

---

### ✅ TODO-9 — Robust scraping (NPE protection)
`services/WerStreamtEsApiClient.java`: `selectFirst(...).childNode(0)` and similar without null checks;
a layout change at werstreamt.es could trigger NPEs.
(Review item #4, correctness; deliberately deferred at the time.)
- **Acceptance criterion:** Null guards + try/catch per entry,
  so that one faulty entry does not abort the whole run.
  Update the outdated user agent (Firefox 2.0.0.6, 2007).
- **Done:** `parseProvider` wraps each provider in try/catch
  and checks the column count before indexing;
  `qualityLabel`/`priceText` and `toSearchResult` are null-safe; user agent updated to a current Chrome.
  Added tests `skipsProviderWithUnexpectedColumnCount` / `skipsMalformedEmWithoutCrashing`.

---

### ✅ TODO-10 — Schema versioning instead of `ddl-auto=update`
`application.properties`: `spring.jpa.hibernate.ddl-auto=update`.
- **Acceptance criterion:** Introduce Flyway or Liquibase for reproducible,
  versioned schemas (prerequisite for TODO-2 and TODO-3).
- **Done:** Implemented via TODO-27 (Liquibase); today `ddl-auto=none`
  (`validate` was an intermediate state, since TODO-40 Liquibase is the only source of the schema).

---

---

## Performance

---

### ✅ TODO-11 — Multiple full resolutions per page view
`services/AggregateService.java`: `getAll()` resolves all entries sequentially.
The Amazon page (`web/DataAggregateController.getAmazon`) called `included()` **and** `paid()`
→ `getAll()` ran **twice** per request.
- **Acceptance criterion:** Call `getAll()` once and apply both filters to the result.
- **Done:** `AggregateService.contentFor(serviceName)` resolves once and returns `included` + `paid`
  (record `ServiceContent`); the Amazon page uses it.

---

### ❌ TODO-12 — `FetchType.EAGER` throughout *(dropped — superseded by ADR-0011)*
`streamingavailability/domain/QueryMeta.java` (`@OneToMany`) and `QueryResultDB.java`
(`@ElementCollection`) load everything eagerly, as does `accountaccess/domain/AppUser.roles`.

- ~~**Acceptance criterion:** Switch to LAZY~~ — **dropped.**
  [ADR-0011](docs/adr/0011-no-open-session-in-view.md) (Accepted) makes EAGER a deliberate
  rule: without Open Session in View, everything has to be loaded before the transaction ends.
  A blanket switch to LAZY would break the ADR, not fulfil it.
- **The original risk has been defused independently:**
  `spring.jpa.properties.hibernate.default_batch_fetch_size=50` (commit `017bd35`,
  measured: 20 titles × 2 providers = 3 statements instead of N+1),
  and the missing FK index arrived with changeset `014`.
- **The agreed approach**, should a collection ever grow too large after all,
  is a targeted fetch join — not LAZY, and certainly not OSIV.

---

---

## Build / Operations

---

### ✅ TODO-13 — Cron pulls pre-release Spring Boot — **deliberately left as is**
Git history: `4.1.0-M1 → M2 → M3 → M4 → RC1 → 4.1.0`.
`upgrade-spring-boot.sh` uses `versions:update-parent` without `-DallowSnapshots=false` and without a milestone filter,
i.e. the cron job automatically pulls milestones/RCs.
- **Decision by the client (2026-09-06): intended.** Picking up milestones is not an
  oversight here, it is the point — it is how the project learns early about upcoming
  Spring Boot versions, and a private project with five users can afford that.
- **What makes this decision viable** is the safety net around it, not the stability
  of the parent: the check run builds with tests through the `verify` phase, and since
  TODO-55 a failure resets the working tree cleanly instead of jamming the update chain. Without those two,
  an unstable parent would have become expensive.

---

### ✅ TODO-14 — `versions-maven-plugin` without a version *(effectively pinned — the premise was wrong)*
`pom.xml` declares the plugin without its own `<version>` — but the assumption that this makes it
"not reproducible" does not hold.

The version comes from the `pluginManagement` of `spring-boot-dependencies`, currently **2.21.0**;
verified against the effective POM and against the actual resolution
(`mvn -B versions:help` logs `--- versions:2.21.0:help ---`).
So no `LATEST` is pulled from the metadata — that is exactly what the empty plugin block is for.
Per commit the build is therefore reproducible, because the parent version is fixed in the POM.

- **What remains is a weaker statement:** the plugin version travels with the
  Spring Boot parent, and that parent is updated nightly, deliberately including milestones
  (TODO-13). An explicit `<version>` would decouple `upgrade-spring-boot.sh` from exactly that
  coupling — that is a trade-off, not a defect.

---

### ✅ TODO-15 — Document/unify the port inconsistency
`server.port=8001` (properties), `EXPOSE 8080` (Dockerfile), `SERVER_PORT=8080` (compose).
It works because compose overrides.
- **Acceptance criterion:** Align the values or explain them in the README.
- **Done:** The values deliberately stay different (compose overrides),
  but are now documented in the README configuration table (`server.port` → "HTTP port (Docker overrides to 8080)").

---

### ✅ TODO-16 — README missing
No setup document existed.
- **Acceptance criterion:** Add a README with setup (place the CSV in `assets/`, profiles, port,
  available endpoints).
- **Done:** Comprehensive README (setup, prerequisites, profiles including `mariadb`/`google`,
  configuration table, complete `## Endpoints` overview).

---

---

## Minor items

---

### ✅ TODO-17 — Cleanup work
- `configurations/JpaConfig.java`: unused import `org.springframework.beans.factory.annotation.Value`.
  → removed in TODO-7 (class was reworked).
- `services/WerStreamtEsApiClient.java` (`search`) and `services/ImdbApiClient.java` (`search`):
  string concatenation in logging (`"Searching for: " + ...`) → parameterized logging.
  → done (WerStreamtEsApiClient in TODO-9, ImdbApiClient here).
- `web/StatusController.java`: `@GetMapping("public/status")` without a leading slash
  (inconsistent with the other mappings). → done: `@GetMapping("/public/status")`.

---

### ✅ TODO-18 — `Price` wraps missing values instead of `null`
`services/WerStreamtEsApiClient.parseAvailability(...)`: missing qualities are stored as `new Price(null)`,
i.e. `availability.sd()` and friends are never `null` but a Price object with `value() == null`.
Callers (e.g. `DataAggregateController.prettyPrint`) however check for `a.fourK() != null`
— which is therefore always true, and `value()` may print `null`.
- **Acceptance criterion:** Model missing prices consistently as a `null` `Price` (Optional/real `null`)
  and adjust the callers accordingly.
  (Spotted during code review test #15.)
- **Done:** `priceOrNull(...)` returns `null` for qualities that are not offered;
  `prettyPrint` (which already checks for `!= null`) therefore no longer prints `null` values.

---

---

## From the re-scan (2026-06-27, after implementing TODO-6/7/8/9/17/18)

---

### ✅ TODO-19 — `/query` bypasses the cache
`rest/QueryController.query(...)` calls `werStreamtEsApiClient.query(...)` **directly**
and therefore scrapes live on every call, while `/search` goes through `StreamInfoService` (cached).
Inconsistent and expensive.
- **Acceptance criterion:** Route `/query` through `StreamInfoService.resolve(...)` as well
  (or remove the endpoint if it is redundant with `/search`).
- **Done (obsolete):** `QueryController`/`/query` no longer exists.
  Today's lookup-by-id endpoint `GET /api/search?imdbId=` (`SearchApiController` →
  `SearchService.resolveByImdbId` → `StreamInfoService.resolve`) already goes through the cache.

---

### ✅ TODO-20 — No central error handling
Scraping/IO errors were rethrown in `WerStreamtEsApiClient` as a bare `new RuntimeException(e)`
and ended up unfiltered as HTTP 500.
- **Done:** A new `domain.ScrapingException` (deliberately in `domain`, not `services`,
  because `ApiExceptionHandler` in the presentation layer would otherwise not be allowed to
  access it according to `ArchitectureTest`) wraps the `IOException` case in `search()`/`query()`.
  `ApiExceptionHandler` maps it to **502 Bad Gateway**.
  Verified live against `mvn spring-boot:run`: `GET /api/search?imdbId=tt0111161` produced a real
  IO error in this environment (egress proxy) and came back cleanly as
  `{"status":502,"title":"Upstream lookup failed","detail":"Query for imdbId 'tt0111161' failed"}`
  instead of an empty 500.
  See also F12 (validation error for the same gap).

---

### ✅ TODO-21 — `ExportReader` aborts the entire import when one row is malformed
`services/ExportReader.parse(...)`: `Integer.parseInt(year)` (NumberFormatException) and
`extractImdbId(url)` (IllegalArgumentException) were not guarded per row — a
single broken row made the whole import (and thus the app startup) fail.
- **Acceptance criterion:** try/catch per row, log and skip malformed rows
  (analogous to the provider robustness from TODO-9).
- **Done:** Row parsing extracted into `toEntry(...)`;
  the loop catches `RuntimeException` per row, logs the line number and skips it.
  The id counter only advances on success (contiguous ids).
  Added test `skipsMalformedRowsAndKeepsIdsContiguous`.

---

### ✅ TODO-23 — `ResponseEntity<?>` with a raw wildcard
`rest/QueryController`: `query(...)` and `search(...)` return `ResponseEntity<?>` —
no type safety for callers/tests.
- **Acceptance criterion:** Concrete return types (`ResponseEntity<List<QueryResult>>` or similar).
- **Done (obsolete):** `QueryController` no longer exists;
  no controller in the codebase returns `ResponseEntity<?>` today (`grep` finds no hits)
  — all REST controllers have concrete return types.

---

### ✅ TODO-24 — Tests for new/untested service logic are missing
After the refactorings, `PreCacheService`, `StreamInfoService.resolveAll(...)`
(caching/threshold/batch miss fetch) and the atomic reload behaviour of `ImdbEntryRepository`
were not covered by unit tests.
- **Acceptance criterion:** Add targeted unit tests (Mockito for the repos/clients).
- **Done:** `ImdbEntryRepositoryTest`, `PreCacheServiceTest`, `StreamInfoServiceTest`
  (cache hit/miss/expiry/forceRefresh/batch).
  TODO-28 surfaced while writing them.

---

### ✅ TODO-25 — Aggregate pages recompute everything on every request
`web/DataAggregateController` + `services/AggregateService`: every provider page calls `getAll()`
and thereby resolved all entries sequentially (beyond TODO-11,
which only concerns the duplicate `getAll()` call on the Amazon page).
- **Acceptance criterion:** Cache/precompute the aggregate results,
  or reuse the batch logic from `resolveAll(...)` (TODO-11/#13 — the reference to TODO-13 was wrong).
- **Done:** `getAll()` now uses `streamInfoService.resolveAll(...)`
  → one batch query instead of N individual queries.
  (Real aggregate caching remains open as an optional later optimization.)

---

---

## From the re-scan (2026-06-28)

---

### ✅ TODO-26 — Error logs without query context
`services/WerStreamtEsApiClient`: the `catch` blocks in `query(...)` and `search(...)` logged
`log.error("Not found %s".formatted(e.getMessage()))` or threw `new RuntimeException(e)`,
without stating **which query/imdbId** the error occurred for.
During the `parallelStream` runs (pre-cache/refresh) there was no way to tell
which entry had failed.
- **Acceptance criterion:** Log the affected query (imdbId or search term) in all error
  output from the client.
- **Done:** `query`/`search` now log and wrap errors with the imdbId/search term.

---

### ✅ TODO-29 — Limit requests per second against werstreamt.es
Pre-cache/refresh fire many concurrent requests at werstreamt.es via `parallelStream`
— impolite and a risk of getting blocked.
- **Acceptance criterion:** Throttle outbound requests;
  configurable property with a sensible default.
- **Done:** `RateLimiter` (global, `synchronized`, minimum interval between requests),
  `WerStreamtEsApiClient.query/search` call `acquire()` before the HTTP GET;
  property `wer-streamt.rate-limit.requests-per-second` (default `2`, `<= 0` disables it).

---

### ✅ TODO-28 — `forceRefresh` was inverted (refresh never refetched)
`services/StreamInfoService.resolve(imdbId, forceRefresh)`: the filter read `forceRefresh || isFresh(...)`.
With `forceRefresh == true` the cached entry was therefore **kept** instead of refetched
— i.e. the `/refresh/*` endpoints (which call `resolve(id, true)`) never updated the data.
- **Acceptance criterion:** `forceRefresh == true` forces a refetch.
- **Done:** Condition corrected to `!forceRefresh && isFresh(...)`;
  spotted while writing the tests (TODO-24).
  Test `resolveForceRefreshAlwaysFetches` covers it.

---

### ✅ TODO-27 — Introduce Liquibase and keep the DB schema as a changelog
The schema was managed by Hibernate via `ddl-auto=update` (see also TODO-10).
- **Acceptance criterion:** Wire in Liquibase, store the complete schema as a changelog
  and switch `ddl-auto` to `validate`,
  so that the schema is reproducible and versioned.
  This is also the prerequisite for the renames from TODO-2/TODO-3.
- **Note (as of June 2026 — ⚠ do not follow this today):** Back then the H2 database held
  nothing but cached scrape results, which is why this entry said that existing deployments
  could simply delete the old `./db`.
  **Today that would destroy user accounts, watchlists, sessions and title metadata**
  — since changesets `003`, `006` and `009` all of that lives in the same database.
  The `/pre-cache` endpoint mentioned here no longer exists either (today `POST /api/cache`).
- **Done:** `spring-boot-liquibase` added;
  baseline changelog under `src/main/resources/db/changelog/`
  (`db.changelog-master.yaml` → `changes/001-baseline-schema.sql`),
  generated from the Hibernate schema (including the TODO-2/TODO-3 names);
  `ddl-auto=validate` in the main and test configuration — **`none` since TODO-40**.
  Tests pass against the schema created by Liquibase.
  The changelog has since grown to 18 changesets (most recently `018-drop-ebay-quota.xml`),
  and the baseline is now an `.xml` file, not a `.sql` one.

---

---

## Architecture review (2026-06-28)

> **Reading note (2026-09-09).** Class and package names in the entries of this and the
> preceding sections reflect the state of June 2026.
> The rework into bounded contexts ([ADR-0014](docs/adr/0014-backend-by-bounded-context-and-ports-adapters.md))
> replaced `services/`, `persistence/`, `web/`, `rest/` and `entities/` as top-level packages;
> today `<context>/{domain,application,port,adapter}` applies (see `CLAUDE.md`).
> The entries have **not** been renamed throughout — that would be a lot of noise for little benefit.
> What has been corrected is where a statement became *factually* wrong, not where a path is merely outdated.

Full analysis: [`docs/reviews/2026-07-28-architecture-review.md`](docs/reviews/2026-07-28-architecture-review.md).
The concrete, actionable items from it:

---

### ✅ TODO-30 — `entities/` is misleadingly named
The package `entities/` contained pure domain records (no JPA entities);
the actual `@Entity` classes live in `persistence/`.
- **Acceptance criterion:** Rename `entities/` → `domain/` (possibly pulling `domainvalues/` in);
  JPA entities stay in `persistence/`.
- **Done:** `entities/` and `domainvalues/` merged into `domain/`
  (`ImdbEntry`, `QueryResult`, `SearchResult` plus the `@Embeddable` values `Availability`,
  `Price`, enum `AvailabilityType`).
  JPA `@Entity` classes stay in `persistence/`.

---

### ✅ TODO-31 — `ImdbEntryRepository` is not a repository
A stateful in-memory catalog, named like a Spring Data repository and placed in `services/`.
- **Acceptance criterion:** Rename to `ImdbCatalog`/`WatchlistStore`,
  clearly separating it from the Spring Data repos in `persistence/`.
- **Done:** Class → `ImdbCatalog` (fields/variables/bean method `imdbCatalog`).

---

### ✅ TODO-32 — Nearly identical provider handlers in `DataAggregateController`
`getDisney`/`getNetflix`/`getWow` (and `getAmazon`/`getGoogle`) differed only in service/view names.
- **Acceptance criterion:** Consolidate data-driven (enum/map from path → service+view),
  reducing ~4 methods to one.
- **Done, and taken further since:** Today there is exactly **one** handler,
  `@GetMapping("/{provider}")` in `ProviderApiController`, resolved via the enum
  `StreamingProvider` (which carries the data table) and `ProviderPageService`.
  Of the helpers from back then only `paidDtos(...)` remains.
  ~~(Explicit routes kept instead of a catch-all `{path}` to avoid routing
  ambiguity.)~~ — **no longer true:** it *is* a catch-all now, which is unproblematic because
  `/api/providers` has no competing route and unknown keys end in a 404.

---

### ✅ TODO-33 — Transaction boundary on a controller
`DataAggregateController` was annotated `@Transactional(readOnly = true)` at class level.
- **Acceptance criterion:** Move transaction boundaries into the service layer;
  controllers must not be transactional.
- **Done:** `@Transactional` removed from `DataAggregateController` **and** `ChangeListController`.
  DB access goes through transactional service methods (`StreamInfoService.resolve/resolveAll`);
  the records returned are detached, so no Open Session in View is needed.
  (Side effect: cache writes on a miss now run in a read-write transaction instead of a read-only one.)

---

### ✅ TODO-34 — View model assembly in the controller
`IndexDto`, `PaidDto` and `prettyPrint(...)` sit inside `DataAggregateController`.
- **Acceptance criterion:** Extract into an assembler/formatter (or DTO factory methods);
  the controller only calls the assembler.
- **Done (obsolete):** `DataAggregateController` no longer exists.
  Today's controllers (`api/CatalogApiController`, `api/ProviderApiController`, …) are thin;
  view model assembly lives in the application layer
  (`application/CatalogOverviewService`, `application/ProviderPageService`).

---

### ✅ TODO-35 — The `invalidated` flag is effectively dead
`QueryMeta.invalidated` was never set to `true`, yet it was filtered on everywhere.
- **Acceptance criterion:** Actually implement invalidation (e.g. invalidate old rows on refresh)
  **or** remove the flag and the query suffix.
- **Done:** Brought to life via TODO-38/TODO-39
  — `invalidateByImdbIds(...)` sets the flag;
  invalidated entries count as "uncached" and get re-scraped deliberately.

---

### ✅ TODO-36 — Provider abstraction for scraping
No interface over "stream availability provider";
hard-wired to jsoup/werstreamt.es (`ImdbApiClient` is dead, see TODO-1).
- **Acceptance criterion:** Interface `StreamAvailabilityProvider`
  (e.g. `List<QueryResult> query(String imdbId)`), implemented by `WerStreamtEsApiClient`;
  bundle connection, user-agent and rate-limit concerns behind it.
- **Done:** Interface `StreamAvailabilityProvider.query(imdbId)` introduced,
  implemented by `WerStreamtEsApiClient`;
  `StreamInfoService` and `QueryController` now depend on the interface (tests mock the interface).

---

### ✅ TODO-37 — `AggregateService.getAll()` returns `List<List<QueryResult>>`
A nested shape that callers immediately flatten.
- **Acceptance criterion:** Return a flat `List<QueryResult>` or a `Map` (like `resolveAll`);
  `included`/`paid` as a single filter with a predicate.
- **Done:** `getAll()` returns a flat `List<QueryResult>`;
  `included`/`paid` share the predicate `on(serviceName)` (combined with `flatrate` or its negation).

---

---

## Invalidation feature (2026-06-28)

---

### ✅ TODO-38 — Invalidate selected entries (UI)
Select entries in the UI and invalidate their cache (for deliberate re-scraping).
- **Acceptance criterion:** Selection in the UI → the marked entries get invalidated.
- **Done:** `QueryMetaRepository.invalidateByImdbIds(...)` (`@Modifying`),
  `PreCacheService.invalidate(...)`, web endpoint `POST /invalidate` and the `/manage` page
  (checkbox selection).
  Integration test against H2 plus Mockito tests added.

---

### ✅ TODO-39 — Scrape only invalidated/missing entries (UI)
A UI that scrapes only the invalidated (or never cached) entries.
- **Acceptance criterion:** A button/endpoint scrapes only the entries without a valid cache.
- **Done:** `PreCacheService.cacheUncached()` (uses `findUncached()`),
  endpoint `POST /scrape-invalidated`, button on `/manage`;
  navbar link "Manage Cache".

---

---

## Bugfixes

---

### ✅ BUG — A provider with multiple language listings was dropped entirely
`WerStreamtEsApiClient` only handled 3 or 6 `.columns.small-4` per provider. If a provider listed
the same title more than once (e.g. Prime Video "Priest" in 3 languages → 9 columns), the
**entire provider** was discarded with `Unexpected column count 9`.
- **Done:** The parser now works per listing row (`.panel.available`), reads the language
  from the title block and deduplicates by (flatrate + prices + language). Several distinct
  listings yield one entry each, distinguished by language (`label()` = "Prime Video (…)");
  a single listing stays without a suffix. New field `QueryResult.languages` +
  column `query_result.languages` (Liquibase `002`). `included()` deduplicates by `imdbId`.
  Integration test against a cleaned-up real detail page (`priest-tt0822847.html`).

---

---

## DB / portability

---

### ✅ TODO-40 — Liquibase changesets from SQL to XML
The changesets were H2-specific raw SQL (`uuid`, `enum('BUY','RENT')`,
`timestamp(6) with time zone`) and therefore not portable.
- **Acceptance criterion:** Changesets as XML using dialect-portable change types; the schema runs
  on H2 **and** MariaDB.
- **Done:** `001-baseline-schema.xml` / `002-add-query-result-languages.xml`
  (`createTable`/`addColumn`/`addForeignKeyConstraint`); dialect-dependent types via
  `${uuid.type}`/`${timestamp.type}` properties. `ddl-auto=none` (Liquibase is the sole
  source of the schema; correctness is covered by the repository tests on H2 + MariaDB).

---

### ✅ TODO-41 — MariaDB as a first-class DB + Testcontainers
- **Acceptance criterion:** MariaDB as a supported DB; the repository test suite runs against a
  Testcontainers MariaDB.
> **Addendum 2026-09-08:** The MariaDB tests now run **as part of the normal `mvn verify`**
> (409 tests instead of 391). They carry `@Tag("testcontainers")`; they are only excluded
> explicitly via `-Pno-testcontainers`, among others in the Docker build stages, which have no
> Docker socket. The text below reads as if they only run when a container runtime happens to
> be around — the deliberate decision is now the opposite, because a check you have to remember
> to run is a check that fails you (`018-drop-ebay-quota` went in unverified for exactly that
> reason). `disabledWithoutDocker` stays as a second net.

- **Done:** MariaDB driver, profile `mariadb` (`application-mariadb.properties`),
  `mariadb` service in `compose.yml`. Repo tests extracted into abstract bases; one H2 variant
  and one MariaDB variant each (`@ServiceConnection MariaDBContainer`,
  `@Testcontainers(disabledWithoutDocker = true)` → skipped without a container runtime, not
  red). H2 stays the default for dev and in-memory tests.

---

---

## Architecture enforcement (2026-07-20)

> **Update (2026-07-29):** The layered architecture described here (presentation → application
> → services → persistence) has been replaced by a functional split along bounded contexts
> (`accountaccess`/`watchlist`/`titlecatalog`/`streamingavailability`, each with its own
> `domain`/`application`/`port`/`adapter` tree) — see the new ADR under
> [`docs/adr`](docs/adr/README.md). `ArchitectureTest` now enforces context isolation instead
> (one rule per context) plus, as before, the `now()` rule; the old layering rule was removed,
> since the "services" layer ended up definitively empty after the switch.

The layered architecture (presentation → application → services → persistence, on top of the
domain leaf) and the "no static `now()` calls" rule ([ADR-0003](docs/adr/0003-time-through-a-timeservice-facade.md))
are enforced with **ArchUnit** (`ArchitectureTest`); in the frontend, ESLint checks the
`now()` rule. Known violations are registered as exceptions and noted here for resolution.

---

### ✅ ARCH-1 — `CommonAttributeService` lived in the services layer but belonged to presentation
`CommonAttributeService` writes the `selectedList` attribute into the Thymeleaf `Model` and is used
only by the `web` controllers — but it sat in the `services` package, so the presentation layer
reached straight into the services layer (the only violation of "presentation depends only on
application (+ domain)").
- **Done:** Moved to `tech.dobler.werstreamt.web` (now a `@Component` of the
  presentation layer) and switched the data source from `ImdbCatalog` (services) to
  `ListSelectionService.currentList()` (application) — so no controller depends on the services
  layer any more. The `ignoreDependency` exception in `ArchitectureTest` has been
  removed; the layering rule now holds without exceptions. (Historical note: the class and
  package names of that time have moved several times since, see above.)
- **Note:** There is no equivalent for the Angular client (the active list comes in there via
  `GET /api/lists`), i.e. the service is purely Thymeleaf-specific.

---

---

## Async cache refresh instead of a synchronous dashboard reload (2026-07-30/31)

Full plan: [`docs/CACHE_REFRESH_PLAN.md`](docs/CACHE_REFRESH_PLAN.md),
decision: [ADR-0016](docs/adr/0016-asynchronous-deferred-cache-refresh.md).
Trigger: the "Manage Cache" page (`/manage`) had no observable effect, because
`StreamInfoService.resolveAll(...)` (dashboard/provider pages) reloaded invalidated/expired entries
synchronously within the same request — a single dashboard visit immediately undid every manual
invalidation before the manage page had anything left to do.

---

### ✅ TODO-43 — Manage table: timestamp instead of a plain "cached" boolean
`ManageRowDto`/`ManageTable` only showed `needsScrape` (yes/no), no point in time.
- **Acceptance criterion:** Each title shows the time of its last scrape (or "never"),
  no longer just a binary pill; an invalidated title still shows "needs scraping".
- **Done:** `QueryMetaRepository.findByImdbIdIn(...)` (without the invalidated filter) + `ManageRowDto.lastScrapedAt`;
  for `needsScrape=false`, `manage-table.ts` shows the formatted timestamp (Angular `DatePipe`)
  instead of the previous "cached" pill (`manage.statusCached` removed).
  Details: `docs/CACHE_REFRESH_PLAN.md`, phase 1.

---

### ✅ TODO-44 — `resolveAll` returns stale data immediately + refreshes in the background
`StreamInfoService.resolveAll(...)` blocked the request on every invalidated/expired
hit instead of returning the values it already had and reloading asynchronously.
- **Acceptance criterion:** An existing but stale cache entry is returned immediately (with
  `stale = true`); the refresh runs deduplicated in the background (`@Async`). Only a
  never-cached title stays synchronous. A new column `due_for_refresh_at` (jitter, rolled on write)
  lays the groundwork for TODO-46.
- **Done:** `resolveAll` returns `Map<ImdbId, ResolvedEntry>` (`results`, `stale`); an
  existing invalidated/expired entry is returned immediately with `stale=true` and
  triggers `StreamInfoService.refreshInBackground(imdbId)` (`@Async("cacheRefreshExecutor")`,
  called via the existing `self` proxy), deduplicated through the new
  `RefreshInFlightTracker` component (`shared/platform/concurrency`); a never-cached title
  stays synchronous. Liquibase `015-query-meta-due-for-refresh-at.xml` adds `due_for_refresh_at`;
  `StreamInfoService.fetch(...)` rolls it on write (`wer-streamt.invalidate.jitter-min-factor`/
  `-max-factor`, default 1.5/2.0). New `AsyncConfig` (`@EnableAsync`, `cacheRefreshExecutor`,
  pool size 2 — the existing `RateLimiter` throttles anyway).
  Details: `docs/CACHE_REFRESH_PLAN.md`, phase 2.

---

### ✅ TODO-45 — "Stale" banner on the dashboard and provider pages
There was no indication when the streaming availability being displayed was out of date.
- **Acceptance criterion:** A small, page-wide notice banner (not an error) appears when
  at least one displayed title is `stale` (no per-row flag, YAGNI).
- **Done:** `CatalogPageDto` (`entries` + `hasStaleEntries`) and `ProviderPageDto.hasStaleEntries`;
  new `StaleDataBanner` component (modelled on `ErrorAlert`, with its own token
  `--mat-sys-secondary-container`) wired into the dashboard and the provider page.
  Details: `docs/CACHE_REFRESH_PLAN.md`, phase 3.

---

### ✅ TODO-46 — Scheduled job for proactive, staggered reloading
Titles nobody looks at go stale indefinitely until they happen to be opened again.
- **Acceptance criterion:** A daily (configurable) job refreshes only the titles that are due
  (invalidated, or TTL × jitter factor 1.5–2.0 elapsed) among the currently watchlisted
  titles — no effect when nothing is due (no needless load when the app isn't used).
- **Done:** `BackgroundCacheRefreshService.refreshDueEntries()` (batch-loads like
  `CacheManagementService.managePage()` and reduces to the newest `QueryMeta` row per
  title, instead of using its own `@Query`) + `adapter/in/scheduled/CacheRefreshScheduler`
  (`@Scheduled(cron = "${wer-streamt.background-refresh.cron:0 0 4 * * *}")`,
  `wer-streamt.background-refresh.enabled` as a kill switch). Shares `RefreshInFlightTracker` and
  `StreamInfoService.refreshInBackground(...)` with the demand-driven path from TODO-44.
  Details: `docs/CACHE_REFRESH_PLAN.md`, phase 4.

---

---

## Bug (2026-07-31)

---

### ✅ TODO-47 — TMDB poster download fails when `title_poster.poster_path` came from the IMDb source
Production log (`tmdb.enabled=true`):
```
WARN t.d.w.t.a.out.tmdb.TmdbPosterSource : TMDB FULL image download
  https://image.tmdb.org/t/p/w500https://m.media-amazon.com/images/M/MV5BMjIzNTA0OTIxNV5BMl5BanBnXkFtZTcwMzA3MTM2Nw@@._V1_.jpg
  returned HTTP 404 (1957 bytes)
```
`TmdbPosterSource.download(posterPath, size)` (`titlecatalog/adapter/out/tmdb/TmdbPosterSource.java`)
always builds the download URL as `imageBaseUrl + "/" + tmdbSize(size) + posterPath` — assuming
that `posterPath` is a TMDB-relative path (e.g. `/abc123.jpg`).
But `title_poster.poster_path` is a single, source-agnostic column (`PosterService.classify`/`storePath`):
if the path was originally determined by `ImdbPosterSource`, it is a **full** Amazon CDN URL
(`https://m.media-amazon.com/...`). If the instance later runs (or runs concurrently, depending on configuration)
with `tmdb.enabled=true`, `PosterService.get(...)` reads that old path from `title_poster` (the row has
no bytes yet for the requested size → `Cached.needsDownload(row.getPosterPath())`) and passes it
through to `TmdbPosterSource.download(...)` unchanged — the two URLs get glued together without a
separator, TMDB answers with a 404, and the poster stays permanently empty for that title
(there is no retry mechanism for "path present, but wrong format").
- **Acceptance criterion:** A `posterPath` that does not match the active source (e.g. it already
  starts with `http` even though TMDB is active) must not be blindly appended to the image CDN URL.
  Either mark/separate the path per source (e.g. a dedicated column or a prefix that is invalidated
  when the source changes), or have `TmdbPosterSource.download(...)` check defensively and treat an
  already absolute `posterPath` (not in TMDB format) like "no poster" (cache negatively, so that
  `findPosterPath` resolves again via TMDB instead of endlessly reusing the same wrong path).
- **Note:** Probably affects every instance that switched its poster source after `title_poster`
  was first populated (`imdb.enabled`/`tmdb.enabled` swapped) — not a one-off.
- **Done:** New `PosterPort.isValidPosterPath(String)` (default `true`), overridden by `TmdbPosterSource`
  (`posterPath.startsWith("/")`) and `ImdbPosterSource` (`startsWith("http://"/"https://")`), each
  narrowed to its own path format. `PosterService.classify(...)` treats a
  `posterPath` that doesn't match the active source as "not resolved yet"
  (`Cached.needsDiscovery()`) instead of passing it blindly on to `download(...)` — the next
  access resolves anew via the currently active source and overwrites both the path **and** the old
  bytes (`TitlePoster.refresh(...)`, self-healing without manual intervention).

---

---

## Feature (2026-07-31)

---

### ✅ TODO-48 — Make the "Manage Cache" UI sortable
The manage table (`/manage`, `ManageTable`) had no sorting — unlike the
availability tables (dashboard/provider pages), which can already be sorted by
title/year/added by clicking the column header (`shared/sort/table-sort.ts`, `MatSortModule`).
- **Acceptance criterion:** The manage table can be sorted by clicking the column header, by
  **name** and by **date** (time of the last scrape, `lastScrapedAt` from TODO-43),
  ascending and descending, following the same pattern (`mat-sort-header`) as the existing tables.
- **Done:** New `sortManageRows(...)` in `shared/sort/table-sort.ts` (a small function of its own
  rather than an extension of `sortRows`, since the manage table has neither `year` nor `added`); rows that
  need scraping (`needsScrape` — never cached **or** invalidated) sort as the
  earliest possible point in time (`-Infinity`): ascending to the top, descending to the bottom.

  **Reversed by the follow-up fix `fc0c5fb`, and that's the interesting part.** Originally
  only `lastScrapedAt = null` was special-cased, and in exactly the opposite direction — by analogy
  with the `year` special case "Not yet released". That missed a case this ticket didn't
  know about: an **invalidated** title keeps its old timestamp. It therefore ended up in the middle
  of the list even though the status pill shows no date at all.
  That's why sorting today hangs off `needsScrape` rather than `lastScrapedAt === null`, and
  `SortableManageRow` carries the field specifically for that. `ManageTable` wires up
  `MatSortModule`/`matSort` like `CatalogTable`; the status column carries `mat-sort-header="lastScrapedAt"`
  (differing from the `matColumnDef` name `status`), since it shows both the "needs scraping" pill and
  the timestamp.

---

---

### ✅ F12 — Controllers bypass `ApiExceptionHandler` via a raw `ResponseStatusException`
`MeApiController` (6×), `WatchlistApiController` (2×) and `ImdbSearchApiController` (1×) threw
`ResponseStatusException` directly instead of a mapped exception, which meant the error message could
be lost without `spring.mvc.problemdetails.enabled`/`server.error.include-message`.
- **Done:** A new `application.ValidationException` (optionally carrying an `HttpStatus`, default
  `BAD_REQUEST`, analogous to `UserManagementException`) replaces all 9 places. `ApiExceptionHandler`
  maps it to a `ProblemDetail` with the respective status. Verified live: a missing
  `theme` field → `400` with `{"detail":"A theme is required.", "title":"Invalid request", ...}` instead
  of an empty default error page; the `tilesPerRow` range check likewise. The two
  `ResponseStatusException` 404 cases (`SearchApiController`, `ProviderApiController`, "unknown
  resource" rather than validation) were deliberately left alone — a different error category, outside
  F12's "400 validation" focus.

---

---

## eBay price lookup (2026-09-05)

---

### ✅ TODO-49 — Existing `save()` calls switched to dirty checking (ADR-0018)
[ADR-0018](docs/adr/0018-dirty-checking-instead-of-an-explicit-save.md) states that entities loaded within a
transaction are mutated and **not** saved — Hibernate's dirty checking writes on commit.
The existing code doesn't follow that yet; five places call `save()` on an
already managed entity:

- `accountaccess/application/UserPreferencesService.java` — `update(...)`, which bundles all six
  preference setters
- `accountaccess/application/UserAdminService.java` — two places (`update`, `deactivate`)
- `titlecatalog/application/PosterService.java` — two places (lines 151, 166)
- `titlecatalog/application/TitleMetaService.java` — line 96
- `watchlist/application/WatchlistImportService.java` — lines 73 and 102

- **Acceptance criterion:** No more `save()` on an entity that was loaded in the same
  transaction. The `of(...)`/`new` branches of the same methods keep their call — there it is
  mandatory.
- **Deliberately not one big commit:** Following the boy-scout convention, whoever touches the
  method anyway converts it. The change is **not mechanical** — for each place you have to check whether the
  entity really was loaded in the same transaction. Blanket-deleting every call
  would take the load-bearing ones with it.
- **Risk when converting:** A mistake is silent. If a place gets converted whose entity was in fact
  detached, the change is lost without an exception and without a log entry. Mockito tests can't
  reveal that — they don't see dirty checking. Where the write is the actual promise,
  a test against a real persistence layer belongs with it.
- **Done on 2026-09-05.** Seven calls removed, one deliberately kept:
  `PosterService.storeBytes` loads via `orElseGet(() -> TitlePoster.of(...))`, so the entity can be
  managed **or** brand new, and the single `save` covers both paths — deleting it would have
  silently lost every first-time poster. Exactly the non-mechanical case this ticket
  warns about.
  The tests now check the effect rather than the mechanism (`verify(..., never()).save(any())` plus
  an assertion on the mutated entity).
  Plus `DirtyCheckingPersistenceTest`: a non-transactional `@SpringBootTest` that re-reads after the
  service commits — the only test that actually proves the write. Verified
  that it catches the failure case: without `@Transactional` on the service method it fails
  (`expected: DARK but was: SYSTEM`).

---

### ✅ TODO-50 — Database indexes evaluated
So far there is exactly one deliberately created index (`014-index-query-cache-imdb-id.xml`); everything
else is primary keys and the unique constraints that bring an index along as a side effect. Whether that
is enough for the actual access paths has **never been checked** — it's an assumption, not a
finding.

The trigger was the then-new quota table from [ADR-0017](docs/adr/0017-quota-management-for-the-ebay-browse-api.md)
(dropped with TODO-56 — the evaluation of the remaining indexes is unaffected by that):
`ebay_user_quota_day` is read on **every** price lookup via `(quota_day, user_id)`. The
unique constraint `uk_ebay_user_quota_day` covers exactly that combination and therefore probably
already carries the query — but "probably" is the reason for this ticket.

- **Acceptance criterion:** For the hot query paths there is an `EXPLAIN` finding showing
  which index is used, or where a full scan happens. Missing indexes get added
  as their own Liquibase changelog, superfluous ones get named.
- **Candidates for the check:**
  - `ebay_user_quota_day` via `(quota_day, user_id)` — per price lookup
  - `watchlist_entry` via `user_id` — every dashboard visit
  - `query_meta` via `due_for_refresh_at` — the staggered background refresh from ADR-0016
    scans on it
  - `title_meta` / `title_poster` via `imdb_id`
  - `spring_session` — comes from the Spring Session schema, not from us, but worth knowing about
- **To keep in mind:** With five users and a few thousand rows, the optimizer will solve some things
  with a full scan anyway, and rightly so. This ticket is about setting indexes **with a reason**, not
  scattering them pre-emptively — every index costs on every write.
- **To clarify first:** The finding has to be gathered against **MariaDB**, not against H2. The
  development and test environment runs on H2, whose optimizer decides differently.

**Done on 2026-09-06.** Gathered against MariaDB 12.3 in a throwaway container, with 2000
seeded `watchlist_entry` and `query_meta` rows — empty tables only yield
"Impossible WHERE" and hence say nothing.

| Access path | Result |
| --- | --- |
| `ebay_user_quota_day` via `(quota_day, user_id)` | uses `uk_ebay_user_quota_day` — the ticket's assumption confirmed, **no** additional index needed |
| `watchlist_entry` via `(user_id, imdb_id)` | `type=const` via the composite unique index |
| `watchlist_entry` via `user_id` alone | **`type=ALL`**, both indexes available, neither chosen |
| `query_meta` via `imdb_id` | uses `ix_query_meta_imdb_id` |
| `query_meta` via `due_for_refresh_at` | **`type=ALL`, `possible_keys=null`** — no index present |
| `app_user` via `username`, `query_result` via `imdb_id`, `title_meta`/`title_poster` via `imdb_id` | a matching unique/regular index present and used in each case |

**One candidate found, deliberately not acted on:** `ix_watchlist_entry_user` is superfluous —
structurally, because `uq_watchlist_entry_user_imdb` covers `(user_id, imdb_id)` and `user_id` is its
left prefix, and practically, because the optimizer demonstrably doesn't pick it (with five users
one of them hits 20% of the table, and there a full scan is cheaper than 400 index lookups). Every
write maintains it for nothing.

**Why it stays anyway — the real insight of this ticket:** the two
databases disagree. MariaDB/InnoDB allows the drop, because the foreign key
`fk_watchlist_entry_user` finds its mandatory index in the composite unique index (verified:
`DROP` succeeded, plan unchanged afterwards). **H2 refuses it**
(`Index "IX_WATCHLIST_ENTRY_USER" belongs to constraint "FK_WATCHLIST_ENTRY_USER"`) — there the
index is tied to the constraint. A changeset that only runs on MariaDB would let the
development and production schemas drift apart; the detour of dropping the foreign key,
dropping the index and re-creating the foreign key would have to be verified against both databases.

For a table with a few thousand rows and five users, that effort is out of all
proportion to the index maintenance it would save. **Re-evaluate when** `watchlist_entry` grows
substantially, or when H2 gets replaced as the development database anyway.

**Deliberately *no* index on `due_for_refresh_at`.** The staggered background refresh (ADR-0016)
scans the whole table. That is right today: the table has one row per cached title,
the run is scheduled and not request-bound, and an index would make every scrape write
more expensive. **Re-evaluate when** `query_meta` reaches five figures or the refresh run takes
noticeably long — then it's selective enough to pay off.

**Methodological note for the next round:** the meaningfulness depends on realistic row counts.
With empty tables the same measurement would have said "all fine" and would not have shown the full scan on
`user_id`.

---

### ✅ TODO-51 — Replaced our own circuit breaker with resilience4j *(code has since been removed)*
> **Moot since 2026-09-07.** Everything described here lived in the `purchaseoffers` context
> and was deleted along with the removal of the price lookup (TODO-56) — including the dependency
> `resilience4j-spring-boot4` and the `micrometer-core` that came with it.
> There is currently **no** circuit breaker left in the application.
>
> **The rationale originally given here was wrong** ("because there is no longer any
> outage-prone third-party service behind a bean boundary"). There are three: werstreamt.es, IMDb and TMDB.
> All that is true is that with `purchaseoffers` the dependency's only user went away —
> not that the need had disappeared. It will be brought back under TODO-66.
>
> The entry stays because of two insights that outlive the code:
> the version research below (the Boot 4 artifact is called `2.4.0`, not `2.3.0`),
> and the reason why the configuration belonged in Java rather than in properties.
> Both still hold for the next attempt.

`TitleOfferService` came with a hand-written circuit breaker: a counter of
consecutive failures plus an `openUntil` timestamp, roughly 15 lines.

**The first assessment was wrong and has been corrected.** Initially I had only checked
`resilience4j-spring-boot4:2.3.0`, read the failure as "there is no Boot 4 artifact"
and therefore advised keeping things as they were. In fact **`2.4.0`** exists. That knocked out two
of the three counter-arguments: the artifact targets this Boot generation, and it does **not** pull in
`kotlin-stdlib` (that came from the `-spring-boot3` artifact). The only new arrival is `micrometer-core`,
which at the same time brings the breaker metrics along; `micrometer-observation`/`-commons` were already
there via Spring Boot anyway.

- **Done:** `@CircuitBreaker(name = "ebay")` on `EbayBrowseApiSource.findOffers` — where
  we actually talk to eBay, and at a bean boundary the aspect can intercept.
  `TitleOfferService` checks the state **before** reserving quota, so that a short-circuited
  call doesn't cost two calls from the daily budget anyway, and handles
  `CallNotPermittedException` for the narrow window in between.
- **What the switch fixes:** Our own breaker counted *consecutive* failures and would **never**
  have tripped against a source that rejects every second call — while half the
  budget ran into failures. Now the error rate over a sliding window decides. On top of that there is
  a half-open state with two probe calls instead of fully reopening.
- **Configuration in Java** (`EbayCircuitBreakerConfig`), not in `application.properties`, for
  two reasons, both of which surfaced during the build: `ignoreExceptions` would be a fully
  qualified class name as a **string** there, and a typo in it silently falls back to the defaults —
  of all places in the rule that keeps an exhausted daily budget from opening the breaker.
  And `src/test/resources/application.properties` **shadows** the production file on the
  test classpath, which makes property values invisible to every `@SpringBootTest`.
- **Verified:** `EbayCircuitBreakerConfigurationTest` checks in the real context that the
  customizer takes effect (otherwise the resilience4j defaults 100/60 s would be in place) and that an
  exhausted quota demonstrably does **not** open the breaker, whereas an ordinary failure
  does.

---

## Feature (2026-09-06)

---

### ✅ TODO-53 — Impersonate other users as an admin
An ADMIN should be able to see the application temporarily as another user, in order to
follow up on reports without having to be given that user's password.

- **Acceptance criterion:** An ADMIN can switch into another user's view from the user
  administration, sees that user's watchlist and settings, and can end the switch again —
  back into their own session, without logging in again.
- **The obvious route:** Spring Security ships `SwitchUserFilter` for this
  (`/login/impersonate?username=…`, `/logout/impersonate`) — no need to build our own. The filter stores
  the original authentication as a `SwitchUserGrantedAuthority`, which is what the way back
  runs on.
- **What has to be settled before implementation — that's the real content here:**
  - **Who may impersonate whom?** An ADMIN impersonating another ADMIN is a path to
    privilege escalation without a trace. At minimum: no impersonating ADMINs, and never
    climbing back up from within an impersonation.
  - **What is visible?** A switch you don't notice is the more dangerous failure. The
    UI needs a permanent, unmissable notice ("You are viewing the application as
    …") with the exit right next to it.
  - **What gets logged?** The start and end of every impersonation belong in the log, with both
    identities. Without that there is no telling after the fact whether a user did something
    themselves or an admin did it in their name.
  - **What may the impersonator do?** Read only, or write as well? Writing in someone else's
    name is the point where a diagnostic tool becomes a question of trust.
  - ~~**Interaction with the eBay quota (ADR-0017):**~~ — moot with the removal of the price lookup
    (TODO-56); `ImpersonationPort` was dropped without replacement, the feature itself
    untouched. The original point read: price lookups during an impersonation
    are charged against the *impersonated* user's quota. Whether that is intended has to be
    decided — otherwise an admin burns someone else's budget.
- **Requires an ADR** as soon as the answers are in: this is a security decision, not a
  question of convenience.

**Done on 2026-09-06**, documented in
[ADR-0020](docs/adr/0020-admin-impersonation-via-switchuserfilter.md). Decisions made by the
client: writing is allowed, ADMIN targets are excluded, price lookups are blocked during
an impersonation.

Two points that only became visible during the build:

- **The way back must not sit under `/api/admin/**`.** During a switch the session carries
  the target's roles, so no `ROLE_ADMIN` — an exit behind the admin rule would be closed
  precisely to the session that needs it. It now lives at
  `/api/impersonate/exit` and is protected by `ROLE_PREVIOUS_ADMINISTRATOR`, the authority
  only a switched session has.
- **A non-admin learns nothing.** The banner hangs solely off `impersonatedBy` from `/api/me`, and
  that field is always `null` for an ordinary user. The action that starts it sits in
  user administration, which is ADMIN-only anyway.

---

---

## Build toolchain (2026-09-06)

---

### ✅ TODO-55 — Hardened `upgrade-spring-boot.sh`
The nightly run took the application down. Three causes, all in the script:

1. **The rollback doesn't roll back.** `handle_error()` calls
   `git reset --hard "$CURRENT_HEAD"` — **`CURRENT_HEAD` is never set anywhere** (the only
   occurrence in the script is this use). The command fails on an empty argument, the
   reset doesn't happen, and the `pom.xml` modified by `versions:update-parent` stays behind in the
   working tree. The next run starts on a dirty tree, `update-and-restart.sh`
   aborts at `git pull --rebase` — **the entire update chain is stuck until someone cleans up
   by hand.** That's the real reason a single failed build turned into a permanent
   state.
2. **We test against the host's toolchain and ship out of Docker.**
   `mvn clean package` in the script uses the host's Node/npm; the later `docker build` uses
   `node:24-alpine`. A Node update on the host therefore breaks the verification run even though the
   artifact itself could be built — and conversely the verification run can be green while the
   Docker build fails. The two should use the same toolchain.
3. **Milestones and RCs are pulled in automatically** (TODO-13). This has since been
   decided to be intentional — it does make point (1) all the more important, though: an unstable parent makes the
   build fail more often, and without a working rollback every one of those failures
   sticks.

- **Acceptance criterion:**
  - Set `CURRENT_HEAD="$(git rev-parse HEAD)"` **before** the first change; additionally,
    on failure, `git checkout -- pom.xml` as belt and braces.
  - The verification run uses the same Node version as the Docker build (either build in the
    container or take the version from a shared source, see TODO-54).
  - ~~No automatic updates to milestones/RCs~~ — rejected, see TODO-13.
  - A failed run leaves behind a **clean** working tree — testable by deliberately triggering the
    error case once.
- **Not verified:** which of the three points caused the actual outage cannot be said
  from here — the host's error message isn't available. Point 1 does explain,
  however, why a one-off failure turned into a permanent state, regardless of what
  triggered it.

**Partially done on 2026-09-06:**

- ✅ Point 1: `CURRENT_HEAD` is set before the first change, aborting if empty, plus
  `git checkout -- pom.xml` on the error path. A failed run therefore leaves behind a
  clean working tree.
- ✅ Point 2: The verification run goes through `docker build . --target verify` instead of `mvn clean package`
  on the host. Verification and delivery now both derive from the same `toolchain` stage in the
  Dockerfile.
- ✅ Point 3: Milestones/RCs are still pulled automatically — intended that way by the client's
  decision, see TODO-13. The point therefore no longer counts as a defect.
- ✅ Followed up on 2026-09-06: The shipped `builder` stage now builds **with** tests. Without
  that, every ordinary commit would have gone into production untested — the nightly verification run
  only kicks in when Spring Boot itself has released something.
- ✅ `cron.sh` decoupled: `upgrade-spring-boot.sh` now distinguishes "nothing to do" (exit 2)
  from "broken" (exit 1). Previously the chain aborted every night without a Spring Boot release, and
  `update-and-restart.sh` never ran — so the application was only redeployed when
  Spring Boot happened to have released something as well.
- ✅ **Added 2026-09-09:** The Docker build could not be run in the development environment
  (Podman got no network there). The proxy fix from TODO-61 has since been rolled out on
  the host and the application is running — so the build did go through.
  **The evidence is indirect**: what's confirmed is that it built and deployed, not that every
  branch of the script has run once. In particular the error path (`handle_error`) remains
  unverified — you only see that when an upgrade really fails.

---

---

## eBay removal and replacement (2026-09-06)

---

### ✅ TODO-56 — eBay price lookup removed
The price lookup via the Browse API (ADR-0017) **worked** — the developer account was
activated, the feature was in use in the application.
It has proven not good enough in production and is therefore being removed.
The state is preserved on the branch `feature/ebay_search` —
nothing is lost here, it just leaves `dev`.

**Where it fell down** (from production, 2026-09-09) — the most important sentence in this entry,
because it stops the next person from building the same thing again:

1. **Opaque.** You saw a number, but not *what* it referred to.
   A price without the offer behind it can't be checked —
   the user has to either believe it or ignore it.
2. **Too many variables in an offer.** Condition and quality of the medium, DVD or Blu-ray,
   director's cut or theatrical version, collector's edition, language version.
   "Cheapest price" lumps together things that are not alternatives to one another.
   The cheapest disc is regularly not the one anybody wants.

**This is not an implementation error but a modelling error** — and it was independent of
the API variant: for physical media, "one price per title" is the wrong abstraction.
No quota, no better search term and no choice of vendor would have changed that.

That's precisely why the replacement (TODO-57) isn't merely the cheaper solution but the more honest one:
the search link doesn't answer the price question at all, it puts the user in front of the
list of offers where those variables are visible — and leaves the decision to the
human, who is the only one who knows which edition is meant.
**The per-user marketplace selection stays**, because the replacement (TODO-57) needs it.

**To settle first — mandatory before deleting, otherwise the application won't start:**
The marketplace selection that stays hangs on two things that live in the context being removed.

1. The enum `Marketplace` (EBAY_DE/EBAY_US/EBAY_GB with marketplace id, currency, base domain)
   lives in `purchaseoffers/domain`.
2. `MarketplaceCatalog` (`purchaseoffers/adapter/in/spi`) implements
   `accountaccess.port.spi.SupportedMarketplaces` — and is the **only** implementation.
   If it goes away without replacement, Spring finds no bean for the SPI,
   `UserPreferencesService` has it injected, and **the application context no longer starts**.
   On top of that, validation of the marketplace input would be gone —
   `app_user.ebay_marketplace` would become a free-text field.

**Settled by TODO-57 (2026-09-06):** The deep link is built entirely in the client,
from `EbayMarketplace` in `core/models.ts`.
The backend contributes nothing to it — it only needs `Marketplace` to validate a
user's setting.
That puts the enum in `accountaccess`, the context the setting belongs to anyway.

That takes care of point 2 along the way: with the enum there, there is no cross-context
dependency left to invert — `SupportedMarketplaces` (`accountaccess/port/spi`)
and `MarketplaceCatalog` both go away without replacement,
instead of being moved into another context.
The inversion that was introduced was tied to the price lookup and disappears with it;
[ADR-0019](docs/adr/0019-port-spi-for-inverted-context-dependencies.md) remains valid
but loses its second use case here
(`PosterAttributionProvider` remains the first).

**Inventory (surveyed, complete):**

| Area | Removed |
| --- | --- |
| Backend | The entire bounded context `purchaseoffers`: `domain` (`Offer`, `OfferPrice`, `TitleOffers`, `OfferLookupResult`, `QuotaDay`, `QuotaVerdict`, `GlobalQuotaUsage`, `UserQuotaUsage`, `OfferSourceUnavailableException`, `UpstreamQuotaExhaustedException`, `Marketplace` — see above), `application` (`TitleOfferService`, `QuotaService`, `dto/OfferDto`, `dto/TitleOffersDto`), `port/out` (`PurchaseOfferSource`, `GlobalQuotaUsageRepository`, `UserQuotaUsageRepository`), `adapter/out/ebay` (`EbayBrowseApiSource`, `EbayOAuthTokenProvider`, `EbayProperties`, `EbayCircuitBreakerConfig`), `adapter/in/api/PurchaseOfferApiController`, `adapter/in/spi/MarketplaceCatalog` — plus all associated tests |
| Frontend | **Careful:** next to every one of these places there has been, since TODO-57, an identically named `showEbayLink` input that **stays** — it carries the search link. A `grep`-driven removal deletes it too, and because Angular inputs have a default, that shows up neither at compile time nor at runtime: the link silently disappears. Removed are: `core/api/offers-api.ts`, `core/offers-store.ts`, `shared/offer-prices/` (component + spec), the `showOffers` input together with the eBay column in `shared/catalog-table/catalog-table.ts`, the chip in `shared/title-tile/title-tile.ts`, the pass-through in `shared/title-grid/title-grid.ts` (the activation in `features/overview/overview-page.ts` has already been removed — see TODO-57), the types `Offer`/`TitleOffers`/`OfferStatus` in `core/models.ts`, the i18n block `offers.*` in `i18n/de.json` and `i18n/en.json`, the column header `table.offers` |
| Configuration | `ebay.*` block in `application.properties` (**excluding** `ebay.default-marketplace` — that belongs to the selection that stays), eBay section in `.env.example`, the two `EBAY_*` lines in `compose.yml`, dependency `io.github.resilience4j:resilience4j-spring-boot4` together with the `resilience4j.version` property in `pom.xml` (used exclusively by this feature) |
| ArchUnit | Rule `purchaseoffers_is_only_accessed_through_its_published_ports`; `purchaseoffers` drops out of the package list of `spring_data_repositories_are_the_port_not_the_adapter`. `bounded_contexts_are_free_of_cycles` stays. |

**Database:** Liquibase `016-ebay-quota.xml` creates `ebay_quota_day` and `ebay_user_quota_day`.
Applied changesets must not be deleted from the changelog —
this needs a **new** changeset that drops both tables.
`017-user-ebay-marketplace.xml` stays untouched.

**Further items to be pulled along:**

- `accountaccess.port.in.ImpersonationPort` was introduced only for blocking the price lookup
  during an impersonation and has no caller left afterwards —
  removed, as are the status `IMPERSONATION_ACTIVE` and the i18n key `offers.impersonating`.
- [ADR-0017](docs/adr/0017-quota-management-for-the-ebay-browse-api.md) becomes moot →
  set to `Superseded`, don't delete.
  [ADR-0020](docs/adr/0020-admin-impersonation-via-switchuserfilter.md) refers to the
  quota interaction → adjust that paragraph.
- `docs/EBAY_PRICE_LOOKUP_PLAN.md` stays as a historical document,
  but gets a status note saying the initiative was discontinued.
- TODO-51 (resilience4j) and TODO-52 (bundle size) refer in part to code being removed;
  TODO-52 rather improves through the removal.

- **Acceptance criterion:** in production code, all that remains of eBay is the marketplace selection and the
  search link; the application context starts; all tests green;
  the two quota tables are removed via a changeset.
  (The original "`grep -ri ebay` no longer hits anything" was not achievable as stated:
  the changelogs `016`–`018` are append-only and remain hits.)

**Done on 2026-09-07, in five steps, each with a green build.**
The plan was checked against the code by a subagent beforehand;
three of its corrections changed the implementation:

1. **`ebay.default-marketplace` does not stay.**
   The plan assumed the property belonged to the selection that stays.
   In fact only `EbayProperties` → `TitleOfferService` read it;
   the default for the selection lives in `AppUser` and `UserPreferences`.
   It goes away along with `EBAY_DEFAULT_MARKETPLACE` in `compose.yml` and `.env.example`.
2. **Two ports the plan didn't mention ended up without callers:**
   `UserDirectoryPort` (+`UserDirectoryService`) and `ImpersonationPort` (+`ImpersonationService`).
   Both go away; the impersonation feature itself is untouched,
   because `MeApiController` reads `ImpersonationConfig` directly.
3. **The inventory had become ambiguous in four places:**
   next to every `showOffers` there has been a `showEbayLink` since TODO-57,
   next to the `offers` column an `ebay` column, next to `.offer-chip` an `.ebay-chip`.
   A `grep`-driven removal would have torn the replacement out with it.

**Also verified against MariaDB** (caught up on 2026-09-08):
`mvn -Ptestcontainers verify` runs in this container — it is a Maven **profile**,
not a `-Dgroups`, which is why the first attempt found zero tests.
409 tests instead of 391, all green.
Since `spring.jpa.hibernate.ddl-auto=none` applies, the schema can only come from Liquibase:
the context started against a real MariaDB, so Liquibase applied the complete
changelog including `018`.
There is no direct log entry per changeset (Liquibase doesn't log that at the default level) —
the evidence is the successful context start, not the line.

---

### ✅ TODO-57 — eBay search link per title on the dashboard
The replacement for the removed price lookup (TODO-56) — in the original plan
([`docs/EBAY_PRICE_LOOKUP_PLAN.md`](docs/EBAY_PRICE_LOOKUP_PLAN.md), section 4) this was
**variant A**, rated there as "trivial, minimal risk, hours instead of days".

Next to every title on the dashboard there is a link that opens the eBay search in a new tab:
search term `"<title> <release year>"`,
marketplace according to the user's setting (`ebayMarketplace` from `/api/me`,
EBAY_DE/EBAY_US/EBAY_GB → `ebay.de`/`ebay.com`/`ebay.co.uk`).

**Why this works where its predecessor failed:**
The link is built in the client — **no server call, no quota, no account,
no circuit breaker**.
There is nothing here that requires being granted access.

**The predecessor's constraint no longer applies here, and that changes the design.**
With the price lookup, every request cost two calls out of a shared daily budget — which is why the
search there was optimised for *one* result and every refinement was a compromise.
A link costs nothing, neither to build nor to leave unclicked.
The search query is therefore allowed to be as good as we can make it, instead of as frugal as possible.

- **URL form:** `https://www.<domain>/sch/i.html?_nkw=<url-encoded search term>`.
  **Not verified** — taken over from the old plan's research.
- **Category filter `&_sacat=…` — the biggest lever on match quality, and the only point
  that ought to be checked before implementation.**
  The old plan named `617` ("DVDs & Blu-ray Discs") for `ebay.de`.
  Without it, "Heat" finds heating supplies; with it, films.
  **Unverified twice over:** neither is the number confirmed, nor that it is the same on `ebay.com` and
  `ebay.co.uk` — eBay category ids are not guaranteed to be identical across
  marketplaces.
  This can be looked up by hand in minutes by running the search once on each of the three
  marketplaces.
  If the ids differ, the category belongs with the marketplace mapping, not in a constant.
- **German title where available and appropriate.**
  A search on `ebay.de` for "Der Pate 1972" hits better than for "The Godfather 1972".
  The German title lives in `TitleMeta.germanTitle` and is reachable in the client via
  `injectTitleMeta` — **but only if it is being loaded anyway**, i.e. if age ratings or
  German titles are switched on (otherwise `title-meta.ts` fetches nothing).
  **Rule:** German title only for `EBAY_DE` and only if it is available without an extra fetch;
  otherwise `OverviewEntry.name`.
  **Explicitly do not trigger a fetch just for the link** — one request per title on
  page load is exactly what this replacement is meant to get rid of.
- **Sorted by total price, ascending — `&_sop=15` ("price + shipping: lowest first").**
  Not a nicety, but the point at which the link replaces what the price lookup was supposed to deliver:
  the old function answered "what does this cost at a minimum?" with a number,
  the link answers the same question with the first hit in the list.
  Without sorting you land on eBay's relevance ordering, and the answer is somewhere in there.
  That the sorting includes shipping is the real correspondence here —
  the old comparison also added price plus shipping
  (`offerTotalCents` in the departing `offer-prices.ts`).
  **The value `15` is not verified.** Tick it off while checking the category filter —
  it's the same operation. If it isn't right: leave the parameter out, don't guess.
- **No link for titles not yet released.**
  `ReleaseYear` uses `0` for "not yet released/unknown".
  In that case the old feature simply dropped the year and searched anyway
  (`TitleOfferService.searchTermFor`) — here the jump-off link is to be omitted entirely instead:
  what hasn't been released isn't being sold either, and a search on the bare title
  yields noise at best.
  That also saves the special case in the search term — it always contains title **and** year,
  or it doesn't exist.
  What remains to be decided is the presentation: render nothing at all, or a disabled hint.
  Suggestion: nothing at all, to keep the row calm.
- **Presentation:** `<a target="_blank" rel="noopener">` like the existing IMDb link,
  but **not** in the title cell:
  across a few hundred rows an inline link would sit at a different x position in every one
  and would no longer be scannable.
  In the table therefore a column of its own, in the grid a badge below the tile.
  Dashboard only, not on the provider pages.
  Both components are also used by the provider pages, so the distinction needs an
  explicit input (as `showOffers` did before) and doesn't follow by itself.
  The reasoning this time is a different one, though: not a budget that needs protecting, but simply
  that the jump-off link doesn't belong there.
- **Accessibility:** the link needs an accessible name that states the title.
  Two hundred rows with the identical link text "eBay" are unusable with a screen reader.
  Same pattern as the old `offers.loadFor`: short visible text, `aria-label` with the title name.
- **i18n** in `de.json` and `en.json`, keys kept in parallel.
- **Tests (Vitest):** URL construction as a pure, testable function
  (marketplace mapping, special characters in the title encoded correctly);
  rendering with `rel="noopener"`, and that with `year = 0` **no** link is produced.
- **No CSP problem:** an `<a href>` is a navigation and is not covered by the fetch directives
  of the Content Security Policy.

- **Acceptance criterion:** A click next to a released title opens, in a new tab,
  the eBay search of the configured marketplace for title and year,
  sorted ascending by price including shipping.
  For a title not yet released there is no jump-off link.

**Implemented (2026-09-06).**
`core/ebay-search.ts` builds the URL as a pure function; the marketplace table there holds host,
category and the question of whether the German title is the better search term here —
per marketplace, not as a single constant, so that a wrong category id costs one line.
It is rendered by `shared/ebay-link/` in two forms —
in the table as a word in a **column of its own**,
in the poster grid as a **badge with the eBay wordmark** in the four brand colours.
One component for both, so the two views don't drift apart
and the same row doesn't search for something different depending on the view mode.
`CatalogTable` and `TitleTile` each get a `showEbayLink` input (off unless set);
only `OverviewPage` switches it on.
`TileEntry` now additionally carries `releaseYear: ReleaseYear | null` —
null for the provider tiles, because `PaidEntry` only supplies the year as finished text
and a back-computed year would be a made-up one.

The two eBay parameters (`_sacat=617`, `_sop=15`) have been checked and confirmed under **TODO-58** —
via `m.ebay.*`, because `www.ebay.*` rejects automated requests with `403`.

**Followed up after the review (2026-09-07):** see commit "Review-Anmerkungen zu TODO-57".
The substance: the German title hung off the age-ratings setting
(and changed under the cursor after the metadata had loaded);
the wiring across three components was untested;
and the dashboard showed the search link **next to** the old price lookup —
two controls named "eBay" per row.
`showOffers` is therefore already switched off on the dashboard;
the code itself goes away with TODO-56.

---

### ✅ TODO-58 — eBay search parameters `_sacat` and `_sop` verified
The search link from TODO-57 carries two values that were never checked against eBay:
the category `_sacat=617` ("DVDs & Blu-ray Discs")
and the sort order `_sop=15` ("price + shipping, lowest first").

**Origin:** `617` comes from the default of the price query that was rolled back
(`EbayProperties.categoryId`) — where it was just as unverified.
There is no way to make up for that from inside the container environment:
eBay answers automated requests with `403`.

**Why this is not cosmetic:** The link sorts by the lowest total price.
Without an effective category filter, what ends up at the top is not the cheapest *copy of the film*
but the cheapest hit of any kind — a poster, an empty case, a keyring.
The filter carries more weight here than it would in a relevance-sorted list.

**To do** (minutes, by hand, in the browser):
1. Run one search per marketplace (`ebay.de`, `ebay.com`, `ebay.co.uk`) and check
   whether `_sacat=617` means the same category there.
   eBay does **not** guarantee category ids across marketplaces.
2. Check whether `_sop=15` really sorts ascending by price including shipping.
3. Record the result in `core/ebay-search.ts`.
   The table there is organised per marketplace, so a correction costs one line;
   `categoryId: null` turns the filter off for a single marketplace.

**Done on 2026-09-07 — both values are correct, the code stays unchanged.**

The route via `www.ebay.*` still fails with `403`.
The **mobile** domain `m.ebay.*`, on the other hand, answers with a real results page
(200, ~100 kB, no captcha) — same parameters, same search engine.
That is the trick that will save time next time.

| Check | Result |
| --- | --- |
| `_sacat=617` on `.de` | ✅ page title: "DVDs & Blu-rays \| eBay" |
| `_sacat=617` on `.com` | ✅ page title: "Heat 1995 **in DVDs & Blu-ray Discs** for sale" |
| `_sacat=617` on `.co.uk` | ⚠️ effective, but never named: film search 296 → 193 hits, "kettle" 32,000 → 370. Consistent with the same category, but **not directly proven** |
| `_sop=15` on `.de` | ✅ first hits 1.50 / 2.49 / 3.00 / 3.90 € plus shipping, ascending |
| `_sop=15` on `.com` | ✅ compared with the same search without the parameter, the order visibly shifts towards the cheaper offers |

**One side finding worth a note:** `_sop=12` does **not** mean ascending.
It was the obvious candidate — the menu label "Lowest price incl. shipping"
sits right next to it in the markup — and yet it returns an unordered list
(4.99 / 58.50 / 5.99 / 2.00 €).
Anyone who "corrects" the value in future makes it worse.
That is why it is in the JSDoc as a warning.

The values remain magic from a foreign system:
`ebay-search.spec.ts` pins them down so that changing one is a deliberate act.
The check does not stop eBay from renumbering tomorrow.

---

### ✅ TODO-61 — After an expired session, a successful login does not lead to the dashboard
**Reported on 2026-09-08 from operations.**

**Reproduction (as reported):**
In a browser where you **were** signed in and were then signed out,
signing in again does not lead to the dashboard —
you stay on the login page, **even though the sign-in succeeded**.

The addition "even though logged in" is the important part:
this is not a failed login but a redirect problem *after* the login.
A fresh browser (or a private window) appears not to be affected, going by what we know so far —
which points at state left over from the expired session.

**What is established about the code** (read, not tried out):

1. `SecurityConfig` sets **no** `defaultSuccessUrl`.
   So Spring Security's default applies, the `SavedRequestAwareAuthenticationSuccessHandler`:
   after login the browser is redirected to the **saved request** if there is one in the session,
   otherwise to `/`.
2. `unauthorized-interceptor.ts` sends the browser hard to `/login` on **every** 401
   (`globalThis.location.href = …`).
3. For `/api/**` an `HttpStatusEntryPoint(401)` is configured.
   That only changes the *response* — the `ExceptionTranslationFilter` still saves the request,
   **before** it invokes the entry point.

**Yesterday's working hypothesis has been disproven.**
It went: the rejected XHR ends up in the session as a `SavedRequest`,
and the login redirects to that API URL instead of the SPA.
`LoginRedirectTest` (new) shows that the chain breaks at the very first link:
an `/api/**` call answered with a bare 401 **does not create a session at all** —
so there is nothing to save.
The test records this because the situation flips the moment someone swaps this entry point
for a redirect: then a session is created, with it the `SavedRequest`,
and the failure mode described becomes real without any test noticing.

**What that rules out** (one test each in
`accountaccess/adapter/in/security/LoginRedirectTest.java`):

| Checked | Result |
| --- | --- |
| Login with no prior history | → `/` → `/app/` ✅ |
| Does the rejected `/api/**` call save anything? | **no** — a bare 401 does not create a session at all, so there is no `SavedRequest` ✅ |
| Does the login leave an authenticated context behind? | yes ✅ |
| Browser navigation vs. XHR on `/app/` | 302 to the login page and a bare 401 respectively ✅ |

Also reviewed and unremarkable:
`SpaController` (`/` → `/app/`, relative to the context path),
the boot sequence in `app.ts` (nothing in it redirects to `/login`),
and the only bail-out in the frontend at all — the 401 interceptor.

**So the bug cannot be reproduced in the backend.**
That narrows things down but proves nothing: the report hinges on real browser state
(cookies from a dead session, `XSRF-TOKEN`, remember-me, cache),
and MockMvc does not model that.

**Trace from operations (2026-09-09) — and out of it a proven bug:**

```
http://domain/w2s/     -> 302  http://domain/w2s/login     <- our response
                       -> 307  https://domain/w2s/login    <- Caddy bends it back
                       -> 200
[Sign in]              -> 302  /w2s/login
URL changed by hand to /w2s  -> 302  http://domain/w2s/app/      <- our response
                       -> 307  https://domain/w2s/app/     <- Caddy bends it back
                       -> 200  (signed in)
```

Two things are now certain.
First: **sign-in works** — the last step reaches the dashboard without a fresh login.
Second, and this is the actual find: **every absolute redirect we build carries
`http://`, even though the request arrived over `https`.** Every `-> 307` in the trace is Caddy
bending our response back.

**Cause:** In the infrastructure, Caddy terminates TLS.
`server.servlet.context-path=/w2s` was set (`compose.yml`),
`server.forward-headers-strategy` was **not** — Spring Boot's default is `NONE`.
So the application believed it was reachable unencrypted
and built every absolute URL with the wrong scheme.
Spring itself only emits a relative `Location`; it is the servlet container that makes it absolute,
and that container only knew about the plaintext hop from the proxy.

**Fixed** with `server.forward-headers-strategy=native`, backed by
`ProxyForwardedHeadersTest` (a real port, because `native` is a Tomcat valve
that MockMvc would never reach — otherwise the test would be vacuously true).

`native` rather than `framework`, and specifically because of the trust boundary:
Tomcat's `RemoteIpValve` only evaluates `X-Forwarded-*` if the peer matches
`server.tomcat.remoteip.internal-proxies` (private ranges by default),
while Spring's `ForwardedHeaderFilter` believes anyone.
We are not directly reachable today — `compose.yml` publishes no port —
but "safe because that happens to be how it is deployed" is a weaker guarantee than
"safe because the code checks".

The price: it **fails quietly**.
A peer outside those ranges means the headers are ignored
and the `http` URLs are back, with no log entry.
A glance at the `Location` of any redirect tells you, after every infrastructure change,
which side of that boundary you are on.
Realistic stumbling block: an IPv6-enabled Docker network —
the default only knows `::1` there.

**The context path stays where it is.**
The Caddyfile uses `handle`, not `handle_path`, so the prefix arrives unchanged —
which makes `server.servlet.context-path=/w2s` the matching counterpart.
The dependencies between the two sides are documented at the setting itself
in [`compose.yml`](compose.yml), not here and not in the code.

**Side note on the `307`s in the trace:** they probably do not come from Caddy at all.
Chrome displays HSTS upgrades as "307 Internal Redirect".
So the browser has been silently repairing our wrong `Location` headers —
which is why it worked unnoticed for so long in the first place.

**What this does not prove.**
The jump to `/w2s/login` after `[Sign in]` **cannot** be derived from the code:
Spring Security's success handler knows only two targets — the saved request
or, failing that, `/` — and neither of them yields the login page.
The fix removes the proven bug and tidies up the chain;
whether it also removes the reported symptom is open.

**Done and confirmed in operations (2026-09-09):** login works flawlessly again.

That also answers yesterday's reservation.
I had written that the fix removes the proven bug but that whether it also removes the symptom
was open — because the jump to `/w2s/login` could not be derived from Spring Security's
success handler.
It could not be derived because it did not originate there:
the wrong scheme in our absolute `Location` headers tipped the chain over,
not the target selection after login.
The lesson for next time: with a redirect chain behind a proxy, look first at
the **scheme of every single response**, not at the target of the last one.

- **Acceptance criterion:** A sign-in after an expired session lands on the dashboard,
  in the same browser session as before, without deleting cookies by hand.
  No more `307` in the chain — every one of our redirects stays on `https`.

---

---

### ✅ TODO-62 — Reconcile the README with the code
The README has been overtaken by the code in many places.
While checking the TODOs, these points came up independently of one another:

| Place | Says | Actually |
| --- | --- | --- |
| `:12` | "Spring Boot 4.1" | 4.2.0-M1 |
| `:37` | `WerStreamtEsApiClient` | `WerStreamtEsSource` |
| `:132` | "`ImdbApiClientTest` is excluded by default" | neither test nor class has existed since TODO-1; no such exclusion in `pom.xml` |
| `:201` | `curl … /check-pre-cache` | endpoint is gone; today `/api/cache/uncached` |
| `:284` | "cache fills up via `/pre-cache`" | today `POST /api/cache` |
| `:264`/`:267` | rate limit defaults `2` | `src/main/resources/application.properties` sets `20` and `10` respectively; the `2` is only the code fallback |
| `:298` | "`mvn verify` starts a container" | the tests hang off Surefire, so they already run with `mvn test` |
| Endpoint table | — | missing, among others, `/api/imdb/…`, `/api/titles/{id}/meta`, the `PUT /api/me/*`, impersonation |
| Feature list | — | mentions neither the eBay search link (TODO-57) nor admin impersonation (TODO-53) |

Not in the README, but deployment-critical and documented nowhere:
`server.forward-headers-strategy=native` and `server.servlet.context-path=/w2s`
— both with **quiet** misbehaviour if they are wrong (TODO-61).

`http-clients/testing.http` also still points at the deleted `/pre-cache` endpoints.

- **Acceptance criterion:** The README's tables and examples match the code.
  It would make sense to think about what can be checked **automatically** while doing so —
  maintaining an endpoint table by hand reliably drifts again.

**Done on 2026-09-09.** All nine points in the table worked through, each verified against the code
beforehand instead of taken from the report (the rate limit defaults, for instance, really are
20 and 10; the `2` was only the `@DefaultValue` fallback).
Added on top: the endpoint table gained `/api/titles/{id}/meta`, `/api/imdb/search`,
the remaining `PUT /api/me/*`, the password reset and impersonation;
the feature list gained the eBay search link and admin impersonation;
the configuration table gained `context-path` and `forward-headers-strategy`
— both deployment-critical and both with **quiet** misbehaviour.
`http-clients/testing.http` still pointed at the deleted `/pre-cache` endpoints.

The most dangerous find was not in the table: for an existing deployment the README advised
"deleting the old data, the cache will refill". That held when the DB only kept scrape results —
today it would hold user accounts, watchlists and sessions.

**What is not solved:** a hand-maintained endpoint table will drift again.
The acceptance criterion asked about that; I do not have an answer.
`DocumentationConsistencyTest` deliberately does **not** check the README — it contains
example URLs and prose on which a path test would only produce noise.

---

### ✅ TODO-64 — Small clean-up finds from the TODO review
Individually too small for a ticket, together an hour of boy scout work:

- **Dead code:** `QueryMetaRepository.findByImdbIdInAndInvalidatedIsFalse(...)` has no
  caller left — displaced by `findByImdbIdIn(...)`.
- **Orphaned Javadoc:** `PreCacheService` claims that the "per-import targeted pre-cache"
  also uses the service — `WatchlistImportService` does not inject it at all.
  `CatalogApiController` describes itself as "the data behind the Thymeleaf `index` page";
  Thymeleaf went away with ADR-0008.
- **`docs/reviews/2026-07-28-architecture-review.md` contradicts the code:** claims `AggregateService` no longer
  exists (it does), and describes the layering as `api/ → application/ → services/ →
  persistence/` (the state before ADR-0014).
- **Superfluous imports** in `StatusController` (imports from its own package).
- **Not enforced:** the fact that there is no `@Transactional` in `adapter/in` holds today — but there
  is no ArchUnit rule for it. A rule would be cheaper than the next relapse.

**Done on 2026-09-09**, each point checked against the code beforehand:

- `QueryMetaRepository.findByImdbIdInAndInvalidatedIsFalse` really did have no caller
  left — removed.
- `PreCacheService` claimed the import used it too. It does not: imported titles are
  resolved on the first page view. Javadoc corrected.
- `CatalogApiController` described itself as "the data behind the Thymeleaf `index` page" —
  Thymeleaf has been gone since ADR-0008.
- Superfluous imports from its own package removed in `StatusController`.
- **The contradiction in the architecture review resolved itself:** the document now sits
  under `docs/reviews/` as a dated snapshot and is allowed to be out of date — its banner
  even names the `AggregateService` error as an example. Correcting a snapshot
  would mean falsifying it.
- **Newly added, because it stood out during the review:** the rule "no `@Transactional` in `adapter.in`"
  did hold, but nothing enforced it. Now there are two ArchUnit rules — and on the first run
  they found three violations that turned out to be **legitimate** (a startup runner and a
  security callback, neither of them HTTP handlers). The rule was then narrowed to `adapter.in.api`:
  nobody believes a rule whose rationale does not cover its own violations.

---

### ✅ TODO-63 — `W2S_ADMIN_PASSWORD` from `.env` binds to no property
`.env.example:11` documents `W2S_ADMIN_PASSWORD` as the initial admin password.
It binds to nothing.

`compose.yml` now only maps `W2S_SECURITY_INITIALADMIN_USERNAME` (line 27);
the corresponding password line was removed when `env_file: .env` was added.
Via `env_file`, `W2S_ADMIN_PASSWORD` does end up in the container, but the property prefix is
`w2s.security` — there is no `w2s.admin.password`.

**Consequence:** whoever sets the documented password does not get it.
`AdminUserSeeder` considers the value empty, generates a random password and **logs it**.
Nobody notices unless they look in the log — you try the password from `.env`,
it does not work, and the obvious assumption is a typo when the account was created.

**The contrast that supports the finding:** `TMDB_API_KEY` was dropped from `compose.yml` in the
same commit and still works via `env_file` — because `tmdb.api-key` is relaxed-binding-capable.
For the admin password the name does not match.

- **Acceptance criterion:** Either restore the line in `compose.yml`, or
  change `.env.example` to the bindable name.
  Then start up once with the password set and check that the seeder logs **no**
  generated password — that is the test that would have exposed the bug from the start.
- **Unverified:** `README.md:212` names `W2S_SECURITY_INITIAL_ADMIN_PASSWORD`, `compose.yml:26`
  insists on `…INITIALADMIN_…`. Both presumably bind via Spring Boot's
  underscore mapping — that is not verified, and the two statements contradict each other.

**Done on 2026-09-09.** The finding was bigger than the one missing mapping: there are **three**
ways in which a `.env` variable can reach a property, and you cannot tell from a variable
which one it takes — mapping in `compose.yml`, a placeholder in a properties file, or
relaxed binding via the name itself. `W2S_ADMIN_PASSWORD` took none, `TMDB_API_KEY` took the
invisible one.

**Solved via placeholders**, symmetrically for username and password, plus a visible line for
`tmdb.api-key`. A placeholder bypasses the naming rule entirely — `${W2S_ADMIN_PASSWORD}` is
an ordinary substitution in which neither dashes nor case matter — and it works on a local
start as well, not only in Compose. The mapping for the username therefore drops out of
`compose.yml`.

**On the naming question, because it was contentious along the way:** Spring Boot's reference says,
for environment variables, "Replace dots with underscores. **Remove any dashes.** Convert to uppercase" —
so what is documented is `W2S_SECURITY_INITIALADMIN_PASSWORD`. Measured,
`W2S_SECURITY_INITIAL_ADMIN_PASSWORD` binds as well, but via an additional path in Spring
Framework's name resolution, not via Boot's documented relaxed binding. I had initially presented the two
forms as equivalent; that was too generous — "works" and "is
guaranteed" are two different things. The comment in `compose.yml` gave the right advice.

**Backed** by `EnvExampleIsWiredUpTest`: every variable from `.env.example` must appear verbatim in
`compose.yml` or in an `application*.properties`. Deliberately blunt — the test does not check whether
relaxed binding would resolve it anyway, because wiring you cannot grep for
is wiring nobody can follow.

The test proved itself while it was being written: the exception list I wrote it
with turned out to be superfluous — both supposed tooling variables are in
`compose.yml`. It was dropped with nothing put in its place.

---

### ✅ TODO-67 — Check the ADRs against reality
20 ADRs: 19 `Accepted`, one `Superseded`. None has ever been reviewed — and the fact that three of
them sat at `Proposed` until 2026-09-09 while already running in production shows that nobody
notices the status field.

**An ADR nobody follows is worse than none** — it looks like a guarantee you can rely on. Two cases
found while checking the TODOs point exactly that way:

- [ADR-0011](docs/adr/0011-no-open-session-in-view.md) ("no OSIV, everything EAGER") holds — but
  TODO-12 demanded the opposite for a year and nobody noticed the contradiction.
- [ADR-0019](docs/adr/0019-port-spi-for-inverted-context-dependencies.md) lost one of its two
  use cases with the eBay rollback. It still holds, but now stands on one leg.

**Three questions per ADR:** does it describe reality? Is it followed — demonstrably, not
apparently? Is its reasoning still the one that would count today?

- **Outcome:** update the status (`Superseded` where overtaken) and the references to it. An ADR
  being broken quietly is **not** a documentation problem — then either the code or the decision is
  wrong, and both deserve their own ticket.
- **Distinct from TODO-65:** the architecture review checks the code against itself, this ticket
  checks the decisions against the code. Sensible to do together — the `architecture-review` skill
  carries the ADR reconciliation as its own area.

**Done on 2026-09-09.** All 20 ADRs were checked against the code by six parallel agents and
translated into English in the same pass; the files were renamed to English slugs and every
reference in the repository pulled through. `DONE.md` was translated too.

**No ADR turned out to be superseded.** Every decision still holds. What the review did find falls
into three groups.

**Two decisions are being broken quietly** — the outcome this ticket was really looking for, since
an ADR nobody follows looks like a guarantee you can rely on:

- ADR-0015 removed request validation from controllers at nine places and missed a tenth
  (`ImdbSearchApiController`), written days before the ADR. → TODO-68.
- ADR-0004 named a countermeasure (`matNativeControl`) that no longer exists, and the test that
  needed it reaches into `MatSelect` internals instead of using a harness. → TODO-69.

**One live falsehood was corrected outside the ADRs.** The changeset comment in
`src/main/resources/db/changelog/changes/018-drop-ebay-quota.xml` still claimed the eBay developer
account "was never granted" and the lookup "never ran" — the last surviving copy of the error that
started this whole review. Editing an applied changeset needs care, so the assumption that
Liquibase excludes `<comment>` from the checksum was verified rather than believed: a file-based
H2 database was migrated, the comment changed, and the migration rerun against it. No validation
error. The comment now says what happened, and says that it was corrected.

**Everything else is text that aged, and stays.** Roughly a dozen ADRs name a class, package or
count that has since moved: `shared.time` became `shared.platform.time`, `ImdbTitleClient` became
`ImdbTitleSource`, "276 backend tests" is now 403, ADR-0020 announces "four constraints" and lists
five. None of it was repaired, on purpose — an ADR records the world it was written in, and
rewriting that turns a decision log into a fiction. The one exception is ADR-0012's
`POST /api/manage/scrape-invalidated`, corrected to `/api/manage/scrape` because that endpoint
never existed under the name the ADR gave it: it had been renamed nine days *before* the ADR was
written. Correcting something that was never true is not editing history.

**Two things the review surfaced that are worth keeping in view**, neither big enough for a ticket:

- ADR-0011 says nothing about `default_batch_fetch_size=50`, which is what actually defuses the
  N+1 risk that its EAGER rule creates. The code documents it in three places and points back at
  ADR-0011; only the ADR does not know. Worth one sentence the next time it is touched.
- ADR-0019 now stands on a single use case. It still holds, but a rule with one instance is a rule
  waiting to be questioned.

---

### ✅ TODO-60 — Serve `PaidEntryDto.year` as a number
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

**Done on 2026-09-09.** `PaidEntryDto.year` is a `ReleaseYear`; the client formats it through
`releaseYearDisplay`, which it already owned.

**The change was larger than the ticket, and in the right direction.** Making the year a number
removed the reason `TileEntry` carried it twice — a formatted `year` plus a nullable `releaseYear`,
with nothing binding the two together. `TileEntry` now has one numeric `year`, and `TitleTile`
formats it where it is shown. That was the acceptance criterion's real intent.

**It also exposed a bug nobody had seen.** The year column sorted *differently depending on the
page*: the paid table received display text, so "Not yet released" fell through `parseInt` to
`NaN` and sorted last; every other table received the number 0 and sorted it first. One column,
two orders. The two halves could not disagree visibly while only one of them was a string, so it
took making them the same type to surface it. Unified on "unreleased sorts last" — the half worth
keeping, since "no year yet" is not "the year zero".

### ✅ TODO-69 — The settings test reaches into `MatSelect` internals
[ADR-0004](docs/adr/0004-vitest-as-the-angular-test-runner.md) chose Vitest with jsdom and named
one countermeasure for Material overlays: a native `<select matNativeControl>` instead of
`mat-select`.
Both halves had come apart — `matNativeControl` occurred nowhere in the repository any more, and
`settings-page.spec.ts` drove the select through `By.directive(MatSelect)`, `open()` and
`.options`, which is the component-internals access the ADR warns against.

- **Acceptance:** either the test goes through a harness, or ADR-0004 says why it does not.

**Done on 2026-09-09. The harness works, so the ADR's escape hatch was never needed.**
`MatSelectHarness` drives the `mat-select` overlay in jsdom end to end: `open()` clicks the
trigger, and the panel is resolved through the harness's `documentRootLocatorFactory()`.
Neither step needs layout, which is the premise the old recommendation rested on.

**The ADR was wrong in a way worth naming.** It generalised "jsdom has no layout" into "CDK
overlays are limited", and prescribed a workaround for a problem it had not measured. ADR-0004 now
states the drawback as what it is — nothing has a layout, so no assertion may depend on a measured
size or position — and records that overlays are reachable, with the mechanism.

**One real restriction did turn up, and it is the harness API rather than jsdom.**
`MatOptionHarness` exposes an option's text, never its bound value.
Since the point of the test is that the *value* is one the server accepts, it now picks each
option and asserts on what the resulting PUT persists.
That in turn exposed an ordering constraint: `mat-select` emits no `selectionChange` for an
option that is already selected, and `EBAY_DE` is the default — so the picks are rotated and
`EBAY_DE` comes last.

**The snackbar claim in the same paragraph was left standing as unmeasured**, rather than quietly
dropped or re-asserted: no spec renders one, they provide a stub `MatSnackBar` instead.

### ✅ TODO-68 — `ImdbSearchApiController` validates in the controller body
[ADR-0015](docs/adr/0015-self-validating-commands-instead-of-scattered-request-validation.md) moved
request validation out of controllers into self-validating command records.
It found the pattern at nine places and removed all nine — but `ImdbSearchApiController`, written
days before the ADR, was missed, and `ImdbSearchService.search(UUID, String)` still took loose
parameters.

- **Acceptance:** an `ImdbSearchCommand(UUID userId, String query)` that validates itself, the
  controller reduced to mapping, and the `ValidationException` gone from the handler body.

**Done on 2026-09-09.** `ImdbSearchCommand` in a new `titlecatalog/application/command/` package,
matching the three contexts that already had one; the handler is two lines; the service takes the
command.

**The point of the ticket was never the tenth site, it was that nothing reported it.**
So the ArchUnit rule went in as well:
`no class in ..adapter.in.api.. constructs a ValidationException`.
Deliberately narrow — this exception type, this package — and it needed **no exemption list**,
which is the sign the boundary was drawn in the right place.
Application services still throw `ValidationException` for the checks a record constructor cannot
make (`UserPreferencesService` needs the database to know a username is taken), and the rule says
nothing about that.

**Expressed as "constructs", not "throws"**, because an unchecked exception leaves no `throws`
clause in the bytecode to match on.
The two coincide here only because nothing catches a `ValidationException` to rethrow it; if that
ever changes, the rule stops being equivalent to its own description.

**Both halves were verified by making them fail, not by reading them.**
The old `throw` was temporarily reinstated and the rule reported "was violated (1 times)" — which
also confirms there is no second violation under `..adapter.in.api..`.
And the strengthened controller test was first run against the *old* controller-body throw to show
it passes there too, so it pins the response rather than the implementation:
`400`, `application/problem+json`, `{"status":400,"title":"Invalid request","detail":"A search
query is required."}`.

**One trivial behaviour change, flagged rather than hidden:** the controller now resolves the user
before constructing the command, so a blank `q` costs one user lookup it previously skipped.
That is the shape ADR-0015 established (`WatchlistApiController` does the same) and it is invisible
to the client.

**ADR-0015 itself was corrected**: it read as though the pattern was gone, and its count of nine
was wrong. It now names the tenth site and points at the rule that enforces it.

### ✅ TODO-54 — Pin the Node/npm version in one authoritative place
The permitted toolchain was stated in four places, maintained separately, and they had diverged:
`.nvmrc` said `24`, `engines` allowed `node >=22 <25`, `packageManager` pinned `npm@11.16.0`, and
`NODE_BASE_IMAGE` built on `node:24-alpine`.
With `engine-strict=true` in `.npmrc`, divergence does not warn — it aborts `npm ci`.

- **Acceptance:** one source of truth for Node and npm, from which the other places are derived or
  against which they are checked.
- **To decide:** whether `engines` follows Angular's own range (`^22.22.3 || ^24.15.0 ||
  >=26.0.0`), which models the gap at 25 correctly.

**Done on 2026-09-09**, as [ADR-0021](docs/adr/0021-track-one-node-lts-major-checked-by-a-test.md):
one Node major at a time, the Active LTS, with `ToolchainVersionsAgreeTest` failing the build the
moment `.nvmrc`, `engines` and the Dockerfile stop agreeing.

**The decision the ticket asked for was made against Angular's range, not with it.** Angular 22
allows `^22.22.3 || ^24.15.0 || >=26.0.0` — a range with a hole in it, because 25 was never an LTS.
Writing that into `engines` would claim support for three majors nothing here builds or tests on.
`engines.node` is now `^24.15.0`: one major, the caret floor taken from Angular.

**Two things turned up that the ticket had wrong, both by measuring rather than reasoning:**

- **Node 25 has been end-of-life since 2026-06-01.** The old upper bound `<25` was carefully
  excluding something already dead. Node 24 is the Active LTS until 2026-10-20; Node 26 becomes
  LTS on 2026-10-28. Read out of the `node-releases` release schedule, because `nodejs.org` is not
  reachable from this environment.
- **`packageManager` cannot be pinned to a major.** The plan had been to keep the field and pin
  only `npm@12`. Corepack rejects that: `npm@12`, `npm@^12` and `npm@12.x` all fail with
  *"Invalid package manager specification … expected a semver version"*; only `npm@12.0.2`
  resolves. Verified with a working Corepack pulled from the registry, since the container's own
  is broken.

  So the field can hold a snapshot or nothing. It was removed — **nothing in this repository
  invokes Corepack** (the Docker build symlinks `npm-cli.js` directly), so it constrained nothing
  while going stale in plain sight. A declaration that binds nothing is worse than none: it reads
  like a guarantee.

**`engines.npm` is `>=11` and deliberately loose:** npm ships with Node, every Node 24 carries at
least npm 11, so a tighter pin could only contradict the interpreter it comes with.

**Checked, not derived.** Generating `.nvmrc` or templating the Dockerfile would buy less than it
costs. The test was verified by breaking it both ways — drifting `.nvmrc` to `26`, and
reintroducing `packageManager` — and it named the offending file in each case.

**What it cannot do, stated in the test itself:** it compares the repository against itself, never
against the outside world. It would not have caught Node 25's EOL, and it will not announce Node 26
becoming LTS in seven weeks.

**A fourth place turned up, by making the mistake.** npm copies `engines` verbatim into
`package-lock.json`, and editing `package.json` alone does not update it — the lockfile sat at
`>=22 <25` for a while with everything green. The Maven build hides it further: its `npm ci` step
is guarded by an `uptodate` check against `package-lock.json`, so a `package.json` edit skips the
install and never revisits the question. `npm ci` therefore did not run in the first full build
after the change, and the claim "this run exercised engine-strict" was wrong until checked.
The test now compares the two files; the fix is `npm install --package-lock-only`.

`engine-strict` itself was verified rather than assumed: setting `engines.node` to `^99.0.0`
produces `npm error code EBADENGINE`, a hard failure, not a warning.

### ✅ TODO-22 — Hard-coded CSV header array
`ExportReader` declared all 18 IMDb column names itself and discarded the file's real header row
via `setSkipHeaderRecord(true)`, so the mapping was purely **positional**.
A wholly foreign file failed loudly; the dangerous case was in between — IMDb inserting one column
or reordering them, after which `record.get("Title")` quietly returned a different field. Rows
still carried a valid `tt…` link and passed, and because the import is a **full sync**, everything
the misread file appeared not to contain was deleted from the user's watchlist.

- **Acceptance:** read the header from the file and validate the five columns actually needed
  (`Created`, `Title`, `Year`, `Your Rating`, `URL`) **once** against it, with a message that says
  what is missing.

**Done on 2026-09-09.** The header comes from the file, the five columns are checked once, and a
file that fails the check is refused with a message naming the missing column, what was expected,
and what the file actually has.

**The refusal happens in `ExportReader.parse`, before `WatchlistImportService` has read a single
stored row** — which is the property that matters: a schema change now aborts the import rather
than deleting the difference. Pinned end to end with the real reader (not a mocked one) in
`WatchlistImportServiceTest`, asserting that `findByUserId` is never even called.

**Two things came along that the ticket had not asked for, both because matching by name changes
what can go wrong:**

- **Duplicate column names are rejected** (`DuplicateHeaderMode.DISALLOW`). With duplicates
  allowed — the commons-csv default — two columns called `Title` would make `get("Title")` pick one
  silently, which is the same quiet wrong-field read this change exists to remove.
- **A leading UTF-8 BOM is stripped.** It was harmless while the header was supplied in code; now
  that the file's own header is the lookup key, a BOM renames whichever column comes first. Today
  that is `Position`, which we do not read — so this guards against the day IMDb reorders, not
  against anything observed.

**The four new tests were checked against the old implementation and all four fail there**, so they
pin the fix rather than describing it.

**No frontend change was needed, and that was verified rather than assumed:**
`watchlist-import-page.ts` already surfaces `err?.error?.detail`, and `ApiExceptionHandler` puts
the message there as an RFC-7807 `detail` on a 400.

**What this does not fix — see TODO-70:** a *partial* format change still deletes. Rows that fail
to parse are skipped individually, so if IMDb changes, say, the date format for only some rows, the
survivors drive a full sync and the rest are removed.

### ✅ TODO-70 — A partly unreadable export still deletes the rows it could not read
TODO-22 closed the case where the *header* changes. A change to the row *values* still slipped
through: rows that failed to parse were skipped individually, the survivors drove a **full sync**,
and every stored title absent from that shortened list was deleted. The import reported success.

- **Acceptance:** a partly unreadable export cannot silently remove stored titles, and whichever
  rule is chosen is stated in the reader's Javadoc next to the header check.

**Done on 2026-09-09**, with the safe rule: **if any row could not be read, nothing is removed.**

`ExportReader.parse` now returns `ParsedExport(entries, unreadableRows)` instead of a bare list,
and `WatchlistImportService` runs the removal half of the sync only when `unreadableRows` is zero.
Adding and updating still happen on a partial file — those only ever write what the file actually
said. Deleting is the asymmetric one: **a row we could not parse and a title the user deleted
upstream are the same absence from the service's point of view**, so a deletion on a partial file
destroys data on the strength of an absence nothing can account for.

**The user is told, and that is not decoration.** Without it the change would be a quiet
downgrade — the user asks for a full sync, gets a merge, and their list keeps titles they removed
on IMDb. `WatchlistImportResultDto` carries `unreadableRows` through to the client, and the import
page shows a **persistent** notice (`.import-notice`, `role="status"`) rather than the four-second
snackbar the success path uses. Translated in both languages.

**The rejected alternatives**, for whoever revisits this: a *threshold* (abort above some share of
skipped rows) needs a number nobody can justify, and gets it wrong in both directions on small
files. *Refusing the import outright* would also throw away the adds and updates, which were never
in doubt. Suppressing only the deletions keeps everything that is safe and drops only what is not.

**Both directions are pinned**: an upload with unreadable rows adds and updates but calls
`repository.delete` never, and a fully readable one still removes — the control case, so the guard
cannot quietly disable the full sync altogether. The frontend notice was verified by suppressing
it and watching the test fail.
