# Restructuring the backend into bounded contexts

This document tells the story of one restructuring effort: turning the backend from a
purely technical layering (`api/` → `application/` → `services/` → `persistence/`) into
four domain-first bounded contexts, each internally organised with pragmatic ports &
adapters.
It complements [ADR-0014](adr/0014-backend-nach-bounded-contexts-und-ports-adaptern.md),
which records the *decision* in Nygard format.
This document instead narrates the *journey*: why we started, the design questions that
came up along the way, what we learned, and what tripped us up — the kind of context an
ADR deliberately leaves out but that's worth keeping for anyone who wants to understand
how the outcome was actually reached, or who's about to attempt something similar.

## Why

The architecture review (`docs/ARCHITECTURE_REVIEW.md`) had already flagged two concrete
symptoms of the old layering, F3 and F8: four separate outbound HTTP-client/rate-limiter
copies, and `PosterService` (`application/`) and `TitleMetaService` (`services/`)
independently reinventing the same non-trivial "self-proxy short transaction, swallow a
racing insert" idiom.
Neither duplication was intentional — both arose because the package structure grouped
code by technical role instead of by business capability, so two classes solving the same
problem never ended up next to each other where the duplication would have been obvious.
A "watchlist" was smeared across four packages
(`domain/WatchlistEntry`, `application/WatchlistImportService`, `services/WatchlistCatalog`,
`persistence/WatchlistEntryRepository`) with nothing in the package structure signalling
that they belonged together.

Alongside the restructuring, the Java package root `tech.dobler.werstreamt` (an old
working-title pun, "wer streamt" = "who's streaming") was renamed to
`tech.dobler.where2stream`, matching the project's already-established name (w2s /
"where-to-stream" — already the Maven `artifactId`/`<name>`, only the Java package had
lagged behind).

## The approach: domain-first, pragmatic ports & adapters

Four bounded contexts emerged from the domain: `accountaccess` (identity, auth, per-user
preferences), `watchlist` (a user's personal list), `titlecatalog` (permanent per-title
facts: poster, age rating, IMDb search) and `streamingavailability` (where a title
streams: scraping, TTL cache, provider aggregation).
Each got its own `domain → application → port → adapter` tree.

"Pragmatic" was the deliberate counterweight to textbook hexagonal architecture, decided
early and re-confirmed several times as real questions came up during migration.
Three kinds of "port" exist in the codebase, but only one of them needed a genuinely new
interface:

1. **Outbound, to an external system** (HTTP/scraping) — already interfaces
   (`PosterSource`, `StreamAvailabilityProvider`), unchanged, just repackaged.
2. **Outbound, to the database** — a Spring Data repository interface *is* the port; JPA
   supplies the adapter as an invisible runtime proxy.
   No hand-written wrapper interface purely for ceremony (more on this below — it took a
   real conversation to land here).
3. **Inter-context** — the one genuine gap.
   `CurrentUserService` and `WatchlistCatalog` were being consumed across context
   boundaries as concrete classes; closing this meant introducing `CurrentUserPort`
   (accountaccess) and `WatchlistCatalogPort` (watchlist), each implemented directly by
   the existing concrete service.
   Other contexts inject the *port* type, never the concrete class.

No interface was introduced for a context's own controller-to-its-own-service calls —
that would have been ceremony without a boundary to protect.

## Design conversations along the way

The plan wasn't executed blind — three points in the migration surfaced real design
questions, and each one was worked through as a discussion before any code changed.
That back-and-forth shaped the final design as much as the original plan did.

**"Do we need both `/api` and `/web` controllers, and should port/adapter live inside
`application`/`services` or as first-level siblings?"**
Raised right at the start, alongside "the port implementation is called a *Service* —
shouldn't it be called an *Adapter*?"
The answer that held up: `adapter/in/api/` (authenticated, SPA-facing) vs.
`adapter/in/web/` (unauthenticated — login, app shell, public status) is a
security/audience boundary, not a rendering-format boundary — `StatusController` returns
JSON too, it's just outside the auth gate.
`port` and `adapter` became first-level siblings of `domain`/`application` in every
context, split by direction: `port/out` (the context reaching *out*, genuinely
implemented by an `adapter/out/*` class — infrastructure adapted to an interface the core
defined) versus `port/in` (the context's own use case, *published* for something outside
to call — implemented directly by the context's own application service, because nothing
external is being adapted).
`CurrentUserService implements CurrentUserPort` was correct as-is; renaming it to
`CurrentUserAdapter` would have been wrong, because the fix needed was reclassifying the
port, not renaming the service.
The user's own reaction to this framework — *"I'm not totally convinced, but we can try
this for now"* — was a fair one; it was accepted provisionally and re-examined at every
subsequent step rather than treated as settled.

**"Why are Spring repositories in `adapter.out` instead of `port.out`?"**
This one was flagged explicitly as a "let's talk, don't just do what I suggest" moment,
and rightly so — it's the crux of what "port" even means here.
Two framings were compared side by side:

- **Framing A** (chosen): the repository interface itself is the port; JPA supplies the
  adapter as an invisible runtime proxy.
  Not technology-neutral in the strict sense (the interface extends
  `ListCrudRepository`, sometimes with `@Query(nativeQuery = ...)`), but it delivers what
  was actually needed — mockability — without maintaining a second, hand-written,
  framework-free port interface plus a delegating adapter for every repository.
- **Framing B** (rejected): the repository is an *adapter* behind a separate,
  hand-written port interface, giving full technology neutrality at roughly double the
  maintenance cost for a persistence-technology swap this project will never make.

Framing A won and is now an enforced ArchUnit rule
(`spring_data_repositories_are_the_port_not_the_adapter`), not just a convention.
One consequence had to be caught after the fact: the per-context isolation rules had to
be narrowed to exempt only `..X.port.in..`, not all of `..X.port..` — otherwise a context
could bypass its own published port and reach straight into another context's raw
repository, since repositories now live in `port.out` too.

**"The `shared` package looks unstructured next to the four contexts — should it follow
the same architecture, or is there merit in keeping it different?"**
Raised only after all four contexts had already settled into their
domain/application/port/adapter shape, at which point `shared`'s flat
`domain/time/outbound/api/web` packages (each added organically, one per step, whenever a
context first needed it) stood out by contrast.
The answer: `shared` isn't a bounded context and has no use case of its own to protect, so
a `port`/`adapter` split would be ceremony without anything to guard.
But "organically grouped by category" still didn't say *why* something lived there, so it
split into `shared/kernel` (value types every context needs — `ImdbId`, `ReleaseYear` —
plus their JPA/MVC adapters) and `shared/platform` (cross-cutting infrastructure several
contexts use but that isn't shared *domain* material — `TimeService`, `RateLimiter`,
`ApiExceptionHandler`, the status/SPA-shell endpoints).
`TimeService`/`SystemTimeService` were already port+adapter in substance (ADR-0003); this
split just made the reason for the rest of the folder's existence explicit without adding
new ceremony.

## Migration sequence

Every step landed as its own commit, only once the full test suite (including
`ArchitectureTest`) was green — no step was left half-done across a commit boundary, and
no REST endpoint path or JSON response shape changed at any point.

- **Step 0 — package rename.** `tech.dobler.werstreamt` → `tech.dobler.where2stream`
  across ~214 files, done first and in isolation from any structural change.
  Explicitly left untouched: `WerStreamtEsApiClient`/`WerStreamtProperties`, the
  `wer-streamt.*` property prefix, `werstreamt.es` URL literals, the
  `src/test/resources/werstreamt/` fixture folder — all of these name the external site
  being scraped, not our own package.
- **Step 1 — Account & Access.** Moved first because nothing else depends on it moving
  first — every other context depends on it, never the reverse.
  Introduced `CurrentUserPort`, establishing the pattern the next port would follow.
- **Step 2 — Watchlist.** Depended only on Account & Access (already moved) plus the
  shared kernel, which was extracted here for the first time (`ImdbId`/`ReleaseYear` +
  their JPA converters), since Watchlist was the first context to need them.
  Introduced `WatchlistCatalogPort`.
- **Step 3 — Title Catalog** (absorbing IMDb search).
  Depended on Account & Access, the shared kernel, and Watchlist's `isOnWatchlist` port
  method.
  Introduced `TitleCacheMaintenancePort`, absorbing the admin poster-warm-up loop that
  used to sit directly in `CacheManagementService`.
- **Step 4 — Streaming Availability** (absorbing the dissolved admin-ops streaming-side
  logic).
  Depended on Account & Access, the shared kernel, and Watchlist's distinct-imdbId port
  methods.
  No new port of its own, since tracing every caller confirmed nothing outside this
  context calls into it.
- **Step 5 — final cleanup.** The old flat packages were empty husks by this point and
  were removed.
  `layers_are_respected` was retired (superseded by the four context-isolation rules plus
  the repository-placement rule); stale references were swept from the README, TODOs.md,
  ADR-0003, a frontend DTO comment, and — found only by a broader sweep, not the original
  dot-literal grep — an env-var-form leftover in `compose.yml`.
  ADR-0014 was written at this point, documenting the settled shape.
- **Shared kernel/platform split** (after Steps 0–5, following the `shared` discussion
  above).
  Pure package moves, no behavioural change; verified with the same full-suite-plus-boot
  discipline as every other step.

"Admin Operations" (`CacheManagementService`/`PreCacheService`/`RefreshService`) was
deliberately *not* modelled as a fifth context — it was never a business capability of its
own, just an admin view spanning Title Catalog and Streaming Availability.
Modelling it as a context would have meant it needed ports into both of the others for no
real benefit over simply dissolving it into Streaming Availability's own maintenance use
case, reaching into Title Catalog through `TitleCacheMaintenancePort`.

## Emerged knowledge

A few things became clear only by doing the migration, not by planning it up front:

- **ArchUnit's `..segment..` predicates match anywhere in the FQN.**
  This meant the pre-existing layered-architecture rule kept passing throughout the
  entire incremental migration without modification — `where2stream.watchlist.application.Foo`
  still satisfies a bare `..application..` predicate — right up until the old flat
  packages were fully empty and the rule could be removed outright.
- **A port that returns a context-local value type publishes that type too.**
  `WatchlistCatalogPort` returning `ImdbEntry`/`WatchlistDate` means the isolation rule
  needs to exempt those types explicitly (`belongToAnyOf(...)`), the same way it exempts
  the `port` package itself — otherwise the rule fights its own port's contract.
- **Cross-context couplings only surface once *both* sides have moved.**
  The isolation rule for a context can only catch a violation after that context's
  packages exist; `MeApiController` (accountaccess) reading `TmdbProperties`
  (titlecatalog) directly was a real, pre-existing bug that had nothing to do with this
  migration's own changes, but it was invisible until titlecatalog got its own isolation
  rule in Step 3.
  Fixed with a new inbound port, `PosterAttributionPort`, with zero JSON contract change.
  Worth remembering for future migrations of this kind: don't assume the isolation rules
  are "done" checking a context just because migration finished — a new coupling can
  surface at any later step, not just the step that "should" own the fix.
- **What's genuinely shared is only visible once code actually moves.**
  The original plan assumed `HttpClientFactory`/`RealHttpClientFactory`/`OutboundHttpClients`
  were cross-context shared infrastructure; tracing the actual code showed they were
  titlecatalog-only all along (their own javadocs already said so).
  Only `RateLimiter` turned out to be genuinely shared, used by both titlecatalog's HTTP
  clients and streaming availability's scraper.
  This kind of thing isn't predictable from reading a plan — it only becomes visible while
  moving the code.

## Impediments

Practical problems hit repeatedly during the mechanical parts of the migration, each
worth naming so a future migration of this shape can watch for them from the start:

- **Prefix collisions in bulk FQN renames.**
  Renaming `AppUser` before `AppUserRepository` (or any short-name/long-name pair sharing
  a prefix) corrupts the longer name mid-string if a naive `perl -pi -e 's/old/new/g'`
  pass runs in the wrong order across a file list.
  Fixed each time either by reordering the rename rules (longest names first) or with a
  targeted post-hoc correction pass; the shared kernel/platform rename was explicitly
  checked for this class of collision before running, and none turned up.
- **A `ugrep`-based `grep` silently skips files it misclassifies as binary.**
  One file (`AppUserDetailsService.java`) contains a UTF-8 em-dash and was invisible to
  plain `grep -rl` calls throughout several migration steps, each time requiring a manual
  fix once the compiler caught what the grep had missed.
  The practice that emerged: use `-Z`/`--null-data` for file-discovery greps (routes to
  real `grep`, not the binary-guessing wrapper), and run a `file`-based sweep after every
  major rename to catch any other such files.
- **Same-package-omission compile errors.**
  Files relying on implicit same-package visibility (no explicit `import`, because two
  classes happened to sit in the same flat package) broke silently the moment one of them
  moved to a new package — this recurred at every migration step and was only fixed
  file-by-file, from the compiler's own error output.
- **A rule outliving its own layer.**
  `layers_are_respected`'s "Services" layer became permanently empty once all four
  contexts consolidated into single `application` packages (Step 4) — dropped from the
  rule as an interim fix, then the whole rule was removed in Step 5 once it was fully
  superseded by the per-context isolation rules.

## Verification, every step

The same discipline ran after every single step, not just at the end: full backend test
suite including `ArchitectureTest` green, frontend test suite green (confirming zero
REST/JSON contract drift — the frontend never needed a code change through any step), and
periodically a live `mvn spring-boot:run` boot smoke test.
By the end of the shared kernel/platform split: 348 backend tests and 7 ArchUnit rules
green, 180 frontend tests green, clean boot.

## Step 6: naming consistency, resolved

The last backlog item from the original plan, Provider/Source/ApiClient naming, turned out
to be two separate questions once every context's `adapter/out/*` was visible side by side,
not one.

`ImdbTitleClient` and `ImdbSuggestionClient` had no port interface at all — application
services depended on them directly, unlike `ImdbPosterSource`/`TmdbPosterSource`, which sit
behind `PosterSource` (now `PosterPort`, see below) precisely because that port has two real
implementations to choose between.
Decision: leave them without a port.
Each has exactly one implementation and nothing outside `titlecatalog` calls them — a port
here would be ceremony without a boundary to protect, the same reasoning ADR-0014 already
used to reject ports for same-context controller-to-service calls.

For the suffix itself, the discussion revealed that "Source"/"Provider"/"ApiClient" weren't
really three names for one role — `WerStreamtEsApiClient` implemented `StreamAvailabilityProvider`
just like `ImdbPosterSource`/`TmdbPosterSource` implemented `PosterSource`, while
`ImdbTitleClient`/`ImdbSuggestionClient` implemented no port at all.
Resolved by converging all five outbound HTTP/scraping adapters on `...Source`
(`WerStreamtEsApiClient` → `WerStreamtEsSource`, `ImdbTitleClient` → `ImdbTitleSource`,
`ImdbSuggestionClient` → `ImdbSuggestionSource`; `ImdbPosterSource`/`TmdbPosterSource` were
already there), and separately renaming the two bespoke port.out interfaces to match the
`Port` suffix `port.in` already used consistently (`PosterSource` → `PosterPort`,
`StreamAvailabilityProvider` → `StreamAvailabilityPort`) — deliberately **not** extending
that rename to the six Spring Data repository interfaces, which keep their
ecosystem-standard `...Repository` suffix (Framing A already established the repository
interface *is* the port; renaming away from `Repository` would fight Spring Data's own
convention for no real gain).

One mechanical pitfall recurred here in a new shape: renaming a class's *file* (`git mv`)
doesn't rename its *declaration* — four test classes (`WerStreamtEsApiClientTest`,
`WerStreamtEsApiClientIntegrationTest`, `ImdbTitleClientTest`, `ImdbSuggestionClientTest`)
kept their old `class` declarations after the file rename, invisible to both the compiler
(package-private top-level classes don't require filename/classname match) and to the
earlier word-boundary-safe `\bOldName\b` content replace (a class named `OldNameTest` has
no word boundary between `OldName` and `Test`, so the regex correctly left it alone — just
not in the way intended). Caught by grepping each renamed file's own `class` declaration
after the fact, not by the test suite.

## `nativeQuery = true` vs. HQL/JPQL: resolved by fixing the actual cause

Flagged during the Framing A/B discussion: `WatchlistEntryRepository`'s two distinct-imdbId
queries used raw native SQL, which sits awkwardly with a repository interface now explicitly
framed as *the* outbound port (see ADR-0009's original "JPQL-Fallstrick" consequence).
The instinct was to just swap `nativeQuery = true` for JPQL text while keeping the existing
`List<String>` + `.map(ImdbId::of)` wrapper — a same-shape, lower-risk change.

Trying it surfaced the real question, though: *why* did the original JPQL attempt
(`select w.imdbId` returning `List<ImdbId>` directly) fail in the first place?
Reproducing the failure showed Hibernate rewriting the query into an implicit constructor
expression (`new ImdbId(w.imdbId)`) that doesn't match `ImdbId`'s actual constructor — a
rough edge specific to *basic/converted* scalar types, not to JPQL as such.
Making `ImdbId` `@Embeddable` (matching the pattern `Price`/`Availability` already use
elsewhere in the codebase) sidesteps that rewrite entirely, because Hibernate treats
embeddables as a known composite type rather than a projection to guess at.
Verified empirically: the direct `List<ImdbId>` projection now works, produces the exact
same generated SQL as the old native query, and let `ImdbIdConverter` — the whole class,
plus its two tests — be deleted outright.
See ADR-0009's 2026-07-29 update for the full before/after.

The lesson: a workaround with a code comment explaining *why* is worth re-examining once
its assumptions are actually tested, not just trusted — the fix here removed a class instead
of just relocating the same complexity.

## CQRS instead of Requests and DTOs: resolved as self-validating Commands

The question wasn't really "full CQRS or not" — a single-database app with no scaling
pressure has no case for separate read/write models or event sourcing, and that option was
ruled out almost immediately.
The actual, concrete problem the research surfaced was narrower: thirteen `*Request` records
with zero Bean Validation anywhere in the codebase, the same `if (request == null || ...)
throw new ValidationException(...)` block repeated near-verbatim across nine controller
methods, and two competing calling conventions into the application layer — some services
took a whole Request object (`UserAdminService.create(CreateUserRequest)`), others took the
same payload unpacked into three or four loose parameters
(`WatchlistImportService.addOne(UUID, ImdbId, String, ReleaseYear)`).

Resolved by introducing `*Command` records in a new `application/command/` package per
context, mirroring `application/dto/`'s existing role for output.
Wire-only `*Request` records stay exactly as dumb as before, bound by `@RequestBody`; a
Command bundles the wire payload together with whatever context the controller resolves
separately (`Authentication`, `@PathVariable`), and validates itself in its own compact
constructor — the same pattern `ImdbId` already uses for its `tt\w+` format, just for
required fields and ranges instead. Every application service method now takes exactly one
Command parameter; the two competing calling conventions collapsed into one.

One assumption got tested before it shaped the design: does a `ValidationException` thrown
from inside a record's compact constructor, invoked by Jackson while deserializing a
`@RequestBody`, still surface as a clean 400 through the existing `ApiExceptionHandler`? It
does — Spring unwraps `HttpMessageNotReadableException`'s cause chain to find a matching
`@ExceptionHandler`, so the response status, `application/problem+json` content type, and
exact `detail`/`title` text are all unchanged from the old controller-side check. That
finding is what made collapsing a Request directly into its Command safe wherever no extra
context needed folding in (`CreateUserCommand`, `InvalidateCommand`).

A second, smaller improvement fell out of unifying the calling convention:
`UserPreferencesService.updateUsername`'s conflict check used to be split across the
controller (call `usernameAvailable()`, inspect the result, throw 409) and the service (do
the actual rename). It now lives entirely in the service, which owns the business rule
end to end — the same shape `UserAdminService.create`'s duplicate-username check already had.

See ADR-0015 for the full decision record, including what didn't make the cut (an
overloaded constructor per Command for wire-type conversion; wrapping every single-value
service call in a Command even where there's nothing to validate).
