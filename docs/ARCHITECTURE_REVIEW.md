# Architecture Review — where-to-stream (w2s)

Date: 2026-06-28. Scope: whole module. This is an architecture-level review (structure,
patterns, consolidation) — not a line-by-line bug hunt; concrete defects live in
[`TODOs.md`](../TODOs.md).

## 1. What the system is

A single Spring Boot module with a clean, layered request flow:

```
IMDb CSV (assets/) ──ExportReader──▶ ImdbEntryRepository (in-memory catalogue)
                                            │
HTTP page/REST ──Controllers──▶ AggregateService / StreamInfoService
                                            │  cache (H2) ◀── QueryMeta/QueryResultDB
                                            └─ WerStreamtEsApiClient ──scrape──▶ werstreamt.es
                                                       │ rate-limited
Thymeleaf views ◀── view DTOs (IndexDto/PaidDto)
```

Layering is mostly sound: controllers → services → (in-memory store + JPA repos + scraper).
Recent work added a config-properties type, a service for pre-caching, a rate limiter,
Liquibase, and unit tests. The foundations are good; the issues below are about **naming,
duplication, and boundary placement** that will bite as the app grows.

## 2. Patterns worth keeping

- **Records for domain values** (`ImdbEntry`, `QueryResult`, `Availability`, `Price`) —
  immutable, readable.
- **Constructor injection + `@RequiredArgsConstructor`** throughout.
- **`@ConfigurationProperties` (`WerStreamtProperties`)** centralises configuration.
- **Extracted, testable parsing** (`WerStreamtEsApiClient.parse(Document, …)`).
- **Immutable-snapshot store** (`ImdbEntryRepository` behind an `AtomicReference`).

## 3. Key findings

### 3.1 The two "model" packages are misleading  🟠
`entities/` holds plain domain records (`ImdbEntry`, `QueryResult`, `Availability`) that are
**not** JPA entities, while the actual `@Entity` classes live in `persistence/`
(`QueryMeta`, `QueryResultDB`). A newcomer reads "entities" and expects JPA.

**Suggestion:** rename `entities/` → `domain/` (optionally merge `domainvalues/` into it).
Keep JPA `@Entity` types in `persistence/`. Pure rename, big clarity win.

### 3.2 `ImdbEntryRepository` is not a repository  🟠
It is a stateful in-memory catalogue of the loaded watchlist, but it is named like a Spring
Data repository and lives in `services/` next to the real ones in `persistence/`. The name
collision makes the data layer confusing (two very different "repositories").

**Suggestion:** rename to `ImdbCatalog` / `WatchlistStore`; the intent (a reloadable
in-memory catalogue) becomes obvious and it stops competing with `*Repository`.

### 3.3 Near-duplicated controller handlers  🟠 (biggest readability win)
`DataAggregateController` has five provider handlers that differ only by service name and
view: `getDisney`/`getNetflix`/`getWow` are identical bar two strings; `getAmazon`/`getGoogle`
share the "paid" shape. This is copy-paste that will keep growing per provider.

**Suggestion:** drive it from data, e.g.

```java
enum ProviderPage { NETFLIX("Netflix","netflix"), DISNEY("Disney+","disney"), WOW("WOW","wow"); … }

@GetMapping("/{page}")
String included(@PathVariable ProviderPage page, Model model) { … one body … }
```

or a `Map<path, (serviceName, view)>`. Collapses ~4 methods into one and makes "add a
provider" a one-line change.

### 3.4 View-model assembly lives in the controller  🟢
`IndexDto`, `PaidDto`, and the `prettyPrint(List<Availability>)` formatter are all nested in
`DataAggregateController`. Presentation logic (price formatting, "leihen/kaufen") is mixed
with request handling.

**Suggestion:** move the DTOs + `prettyPrint` into a small assembler/formatter (or onto the
DTOs as factory methods — `PaidDto.from(...)` already points that way). Controllers then
just call the assembler.

### 3.5 Transaction boundary sits on a controller  🟠
`DataAggregateController` is `@Transactional(readOnly = true)` at class level. Transaction
demarcation belongs in the service layer; a transactional controller leaks a persistence
concern into the web layer and keeps the EntityManager open across view rendering.

**Suggestion:** move `@Transactional(readOnly = true)` onto the service methods
(`AggregateService`, `StreamInfoService`) and drop it from controllers.

### 3.6 `invalidated` flag is effectively dead  🟢
`QueryMeta.invalidated` is always constructed `false` (no code ever sets it `true`), yet every
lookup filters on `…InvalidatedIsFalse`. It is a no-op column today.

**Suggestion:** either implement invalidation (e.g. mark old rows invalid on refresh instead
of relying solely on the age threshold) or drop the flag and the query suffix.

### 3.7 Scraper coupling & dead client  🟢
HTTP concerns are spread over `ApiClientUtils` (static), per-client `baseUrl`, and the new
`RateLimiter`. `ImdbApiClient` is dead/broken (TODO-1). There is no abstraction over "a
stream-availability provider", so the app is hard-wired to jsoup + werstreamt.es.

**Suggestion:** introduce a small `StreamAvailabilityProvider` interface (method:
`List<QueryResult> query(String imdbId)`), implemented by `WerStreamtEsApiClient`. Group the
connection/User-Agent/rate-limit concerns behind one collaborator. Enables fakes in tests and
future providers without touching `StreamInfoService`.

### 3.8 Awkward `List<List<QueryResult>>`  🟢
`AggregateService.getAll()` returns a list-of-lists that callers immediately flatten. The
per-entry grouping carries no information downstream.

**Suggestion:** return a flat `List<QueryResult>` (or a `Map<imdbId, …>`, which
`resolveAll` already produces) and express `included`/`paid` as one filter method taking a
predicate.

## 4. Cross-cutting gaps (already ticketed)

- No central error handling / bare `RuntimeException` wrapping — **TODO-20**.
- `FetchType.EAGER` throughout the persistence aggregate — **TODO-12**.
- Unauthenticated state-changing `GET`s — **TODO-5**.
- `/query` bypasses the cache — **TODO-19**.

## 5. Suggested order of work

1. **Rename `entities/` → `domain/`** and **`ImdbEntryRepository` → catalogue** (§3.1, §3.2)
   — pure-rename clarity, do first while the surface is small. (TODO-30, TODO-31)
2. **Collapse the provider handlers** (§3.3) — highest duplication payoff. (TODO-32)
3. **Move tx boundaries to services** + **extract the view assembler** (§3.5, §3.4).
   (TODO-33, TODO-34)
4. **Provider interface** (§3.7) and **flatten `getAll`** (§3.8) when a second provider or
   more aggregation is on the horizon.

The actionable items above are tracked as **TODO-30 … TODO-34** in `TODOs.md`.
