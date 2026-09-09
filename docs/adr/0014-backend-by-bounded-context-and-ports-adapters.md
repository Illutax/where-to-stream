# 0014. Backend organised by bounded contexts, with pragmatic ports & adapters

- **Date**: 2026-07-29
- **Status**: Accepted
- **Update (2026-07-29):** `shared` itself has been split into `shared/kernel/` (the domain value
  types `ImdbId`/`ReleaseYear` plus their adapters: the JPA `AttributeConverter`, the Spring MVC
  `Converter`) and `shared/platform/` (`time/`, `outbound/`, `api/`, `web/` — unchanged, only
  moved). The reason: `shared` was sorted purely technically, by category (grown organically, one
  folder per step as soon as some context needed it first), without it being apparent *why*
  a class lived there. The split makes that explicit: "every context needs exactly this
  value type" (kernel) vs. "none of this is domain enough for a context of its own, but several
  contexts need it" (platform). **No** full `domain/application/port/adapter` for `shared` —
  it is not a bounded context, it has no use case of its own, and a `port`/`adapter` split with
  nothing to protect would be pure ceremony. `TimeService`/`SystemTimeService` are already
  port + adapter, just without the folder renaming — that is enough.
- **Update (2026-07-29):** Naming consistency between port interfaces and the classes that
  implement them (step 6 of the original migration plan).
  `PosterSource`/`StreamAvailabilityProvider` (the interfaces) were named differently from what
  `port.in` has long been doing consistently (`CurrentUserPort`, `WatchlistCatalogPort`,
  `TitleCacheMaintenancePort`, `PosterAttributionPort` — all with a `Port` suffix).
  Renamed to `PosterPort`/`StreamAvailabilityPort`, so that **every** port interface (`in` as well
  as `out`) is recognisable by its `Port` suffix — with one deliberate exception: the six Spring
  Data repository interfaces (`WatchlistEntryRepository` etc.) keep their `Repository` suffix,
  because that is the immediately recognisable Spring Data convention and framing A had already
  settled that "the repository interface is the port" anyway — an additional rename would bring no
  new insight, only friction with the ecosystem.
  On the adapter side, `Source` was chosen as the uniform suffix for all five outbound
  HTTP/scraping adapters (including the ones that have no port interface) — `ImdbPosterSource`/
  `TmdbPosterSource` stayed unchanged, `WerStreamtEsApiClient` → `WerStreamtEsSource`,
  `ImdbTitleClient` → `ImdbTitleSource`, `ImdbSuggestionClient` → `ImdbSuggestionSource`.
  Deliberately **no** new port interface for the latter two (see the alternative below) — both
  have exactly one implementation and nothing outside Title Catalog calls them, a port
  would be pure ceremony, exactly the same reasoning as for the `shared` split above.

## Context

The backend was layered purely technically:
`api/` → `application/` → `services/` → `persistence/`, on top of a `domain/` leaf, enforced by `ArchitectureTest` (ArchUnit).
This layering says nothing about which business capability a feature belongs to —
a "watchlist" was spread across four packages (`domain/WatchlistEntry`, `application/WatchlistImportService`,
`services/WatchlistCatalog`, `persistence/WatchlistEntryRepository`), without the package structure making that visible.
Two concrete symptoms of this:

- **`PosterService`** (`application/`) and **`TitleMetaService`** (`services/`) independently
  implement the same non-trivial idiom (short transactions, enforced via a self-proxy, around a
  cache read/write, so that no DB connection is held across a slow network call; see ADR-0011) —
  just because one class happened to be filed under `application` and the other under `services`,
  you could not see when reading them that both solve the same problem.
- Four classes (`ImdbTitleClient`, `ImdbPosterSource`, `TmdbPosterSource`, `ImdbSuggestionClient`)
  independently build their own `RateLimiter`/`HttpClient`, even though in domain terms they all
  belong to "how do we get title metadata" —
  the flat layering did not make that visible, because they all sat in the same `services/`
  package next to code they had nothing to do with.

On top of that, the Java package was still called `tech.dobler.werstreamt`
(an old pun from the naming phase, "wer streamt"),
while the project name had long settled on w2s / "where-to-stream" (already the
Maven `artifactId`/`<name>`).

## Decision

**Slice by domain before technology**: the backend is now organised around four bounded contexts,
each with its own `domain` → `application` → `port` → `adapter` structure (ports & adapters within each context),
plus a deliberately minimal `shared` kernel:

```
tech.dobler.where2stream/
  shared/
    kernel/                  -- ImdbId, ReleaseYear + their adapters (JPA converter, MVC converter)
    platform/                -- time/, outbound/ (RateLimiter), api/ (ApiExceptionHandler), web/
  accountaccess/             -- identity, auth, admin user management, user preferences
  watchlist/                 -- a user's personal list
  titlecatalog/               -- permanent title metadata (posters, age ratings) + IMDb search
  streamingavailability/      -- werstreamt.es scraping, TTL cache, provider aggregation
```

Every context: `domain/` (domain values + entities), `application/` (use-case services),
`port/in/` (the interface published for other contexts, where there is one),
`port/out/` (outward dependencies — DB, external APIs), `adapter/in/` (controllers),
`adapter/out/` (implementations of the `port/out` interfaces).

**Pragmatic ports**, no ceremony: there are three kinds of "port", but
only one of them really needed a new interface.

1. **Outbound, to an external system** (HTTP/scraping): always was an interface
   (`PosterSource`, `StreamAvailabilityProvider`) — unchanged, only moved.
2. **Outbound, to the database**: a Spring Data repository interface **is** the port (JPA supplies
   the adapter as a generated runtime proxy) —
   no additional wrapper interface just for form's sake.
   Accordingly, all repository interfaces now live under `port/out/`, not under
   `adapter/out/` (see "Framing A vs. B" below).
3. **Between two contexts**: the only genuinely new construction.
   A context that publishes a capability for others does so through an explicit
   interface under `port/in/` —
   e.g. `CurrentUserPort` (accountaccess, resolves username → UserId) or
   `WatchlistCatalogPort` (watchlist, reads watchlist facts per user or globally).
   Other contexts inject the *port* type, never the concrete class.
   For a context's own controller-to-own-service relationship (within the same context), by
   contrast, there is deliberately no interface —
   that would be pure ceremony.

**`port/in` vs. `port/out`, precisely**: `port/in` is the capability a context *publishes*
(called from outside, the way a controller calls its own service —
except that here the caller is another context).
`port/out` is a context's dependency *outwards* (DB, external API) —
"adapter" is the right word there, because that is where infrastructure really is adapted to an
interface we defined.
With `port/in`, on the other hand, there is nothing to adapt —
the implementation is simply the context's own application service
(`CurrentUserService implements CurrentUserPort`), not a separate "adapter" object.

**Framing A vs. B (is the repository interface the port or the adapter?)**: A was chosen
deliberately — the repository interface itself is the port, JPA the (invisible) adapter.
That is not technology-neutral in the strict sense (the interface extends `ListCrudRepository`,
in places with `@Query(nativeQuery = ...)`), but
it delivers what is actually needed here — testability via mocks — without having to maintain a
second, hand-written port interface plus a delegating adapter for every repository (the
"strict" alternative B).
`ArchitectureTest` now enforces framing A explicitly (`spring_data_repositories_are_the_port_not_the_adapter`).

**Isolation, enforced by ArchUnit**: one rule per context —
no code outside a context may reach into its internals, only into its `port/in`.
The exemptions are narrow and documented:
`shared..` (the `ApiExceptionHandler` deliberately knows every context-specific kind of exception), and the
read-model types a port itself returns (e.g. `ImdbEntry`, `WatchlistDate` for
`WatchlistCatalogPort` — those are part of the published contract, not internals).
One of these rules actually caught an existing, previously invisible architecture violation:
`MeApiController` (accountaccess) read `TmdbProperties` (titlecatalog) directly in order to
determine the TMDB attribution flag in `/api/me` —
fixed with a new port (`PosterAttributionPort`), without changing the JSON response.

**"Admin Operations" is dissolved, not a context of its own**: `CacheManagementService`,
`PreCacheService` and `RefreshService` previously spanned Title Catalog and Streaming Availability —
not a self-contained domain concept, but an admin view across two other contexts.
They are now Streaming Availability's own "cache maintenance" use case, which reaches into the
other contexts via `WatchlistCatalogPort` and Title Catalog's `TitleCacheMaintenancePort`.
`ManageApiController`/`RefreshApiController` keep their exact URLs (the Angular admin UI
depends on them) and now move into Streaming Availability.

**Rename**: `tech.dobler.werstreamt` → `tech.dobler.where2stream` (purely mechanical, as
a separate first step, ~214 files).
Explicitly **not** touched:
`WerStreamtEsApiClient`, `WerStreamtProperties`, the `wer-streamt.*` property prefix,
the `werstreamt.es` URL literals, the `src/test/resources/werstreamt/` fixture folder —
those name the external site actually being scraped, `werstreamt.es`, not our package.

**Migration**: incremental, one bounded context per commit (Account & Access first, since every
other context needs it while it needs none itself;
then Watchlist, Title Catalog, Streaming Availability), with a green test suite after every step.
The `shared` kernel emerged along the way: `TimeService`/`ApiExceptionHandler` with the
first context that needed them;
`ImdbId`/`ReleaseYear` with Watchlist;
`RateLimiter` only with Title Catalog (it turned out to be shared across two contexts,
whereas `HttpClientFactory` turned out to be Title-Catalog-internal — both only became visible when
the code was actually moved, not plannable beforehand).

## Consequences

**Easier / better:**

- Business capabilities are now visible from the package — "watchlist" is one package, not four.
- Cross-context access is explicit (a port type in the constructor) instead of implicit (some
  concrete class from another part of the `services` package).
- `ArchitectureTest` now actively enforces the boundaries —
  an accidental access to a context's internals fails at the next `mvn test`, not at
  the next architecture review.
- `PosterService`/`TitleMetaService` now sit side by side in `titlecatalog/application/` —
  their shared idiom is immediately visible when reading, and extracting the
  common logic in the future has become the obvious next step (but has not been done yet —
  this ADR describes the structure; the duplication itself was not eliminated in this pass).

**Harder / drawbacks:**

- Deeper package paths (`titlecatalog.adapter.out.imdb.ImdbPosterSource` instead of `services.ImdbPosterSource`).
- Three named concepts (`port.in`, `port.out`, `adapter`) instead of two (`services`, `application`) —
  more to learn for new contributors, even if each concept on its own is clearer.
- The migration uncovered two cases where files informally relied on shared packages
  (no `import`, because both happened to sit in the same flat package) —
  when they were moved, these places needed an explicit `import` that had not existed before.
  Not a structural problem, but a migration step that recurred with every further context.

## Alternatives Considered

- **Keep the flat layering and only improve naming conventions/documentation**: costs nothing,
  but does not solve the actual problem —
  the F3/F8-style duplication (see `../reviews/2026-07-28-architecture-review.md`) arises precisely *because* the
  package structure tears apart code that belongs together in domain terms.
- **"Admin Operations" as a fifth context of its own**: would have implied that cache management is
  a business capability in its own right —
  it is not, it is an admin view onto two existing contexts.
  Modelled as a context, it would itself have needed ports to both, with no real gain
  over the chosen dissolution.
- **Strict ports & adapters (framing B) for every repository**: every repository would get a
  hand-written, framework-free port interface plus a delegating adapter.
  It delivers full technology neutrality,
  but doubles the maintenance effort for a property (swapping out the persistence technology) that this
  project will never make use of — rejected in favour of framing A.
- **A single generic ArchUnit slice rule instead of four explicit per-context rules**: ArchUnit's
  `slices()` API is suited to cycle detection,
  but not cleanly to "outside X only via `X.port.in`" —
  four explicit, well-commented rules are more readable here than a generic construction that
  merely hides the same exemption logic.
