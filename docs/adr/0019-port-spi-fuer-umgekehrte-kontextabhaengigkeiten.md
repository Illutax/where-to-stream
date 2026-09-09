# 0019. `port.spi` for inverted context dependencies instead of parking them in `shared`

- **Date**: 2026-09-05
- **Status**: Accepted (added retrospectively on 2026-09-09 — implemented and in production)

## Context

[ADR-0014](0014-backend-nach-bounded-contexts-und-ports-adaptern.md) organises the backend by
bounded contexts with `port.in` (what others may call on us) and `port.out` (our own dependencies on
the database and on foreign systems). `ArchitectureTest` enforces per context that only `port.in` is
reachable from outside.

While building the eBay price lookup we noticed a **cycle** between `accountaccess` and
`titlecatalog`:

- `MeApiController` (accountaccess) needed `titlecatalog.port.in.PosterAttributionPort` in order to
  give `MeDto` the "show TMDB attribution notice" flag.
- `ImdbSearchApiController` (titlecatalog) needs `accountaccess.port.in.CurrentUserPort`.

Both edges ran through *published* ports, so both were permitted under the existing rules. The rules
still did not report the cycle, and that is not sloppiness but structural: every isolation rule
checks **one** direction. A cycle consists of two edges each of which is allowed on its own.

The first attempt at a fix was to move `PosterAttributionPort` to `shared/platform/api`. That turns
the rule green, because `shared` is exempt from the isolation rules — but it **does not remove the
coupling, it hides it**. Followed through consistently, this route lands every interface in `shared`
as soon as it becomes inconvenient, and `shared` degenerates from the place for cross-context
building blocks into a drawer for unresolved dependencies. The attempt was therefore rejected.

The actual problem is the **direction** of the dependency, not its location:
`accountaccess` knows nothing about poster sources and is not supposed to. What it has is a *need*
("a flag for `/api/me`") that another context can meet.

## Decision

A context that needs something another context can supply **declares its own interface for it and
lets the other context implement it.** Such interfaces live in `<context>/port/spi/` and are
published.

Each context therefore has three kinds of port with clearly different meanings:

| Package | Meaning | Visible from outside? |
| --- | --- | --- |
| `port.in` | What others may **call** on us | yes |
| `port.spi` | What others may **implement** for us | yes |
| `port.out` | Our own dependency on the DB/a foreign system | **no** |

Concretely implemented:

- `accountaccess/port/spi/PosterAttributionProvider` declares the need.
- `TmdbProperties` (titlecatalog) implements it — the context that knows which poster source is
  active.
- The dependency now only points `titlecatalog → accountaccess`, in the same direction as the one
  that already existed via `CurrentUserPort`. The cycle is gone, not renamed.

In addition, `ArchitectureTest` now enforces **freedom from cycles between the contexts**
(`bounded_contexts_are_free_of_cycles`). `shared` is excluded there in both directions:
`ApiExceptionHandler` maps the exception types of all contexts, so `shared` inevitably depends on all
of them and all of them on `shared`. Without that exemption the rule would be permanently red and
therefore worthless.

**When `shared` is nevertheless the right place:** for building blocks that belong to no context and
have no direction — value types of the shared kernel (`ImdbId`), technical cross-cutting services
(`TimeService`, `RateLimiter`, `HttpClientFactory`). The distinguishing criterion is not convenience
but the question of whether there is a natural owner. `PosterAttributionProvider` has one: the
context that needs the value.

## Consequences

**What gets better**

- **The coupling is visible and directed.** From `port.spi` a reader can see that another context
  contributes something here, and at which end the need arises.
- **`shared` stays small.** There is now a named alternative for the case that would otherwise have
  made it grow.
- **Cycles get reported**, and by a rule that has been verified to fail when an edge is put back in.
- The dependency-inversion idea is thereby written down once and doesn't have to be re-derived for
  every similar case.

**What gets harder**

- **A third kind of port is conceptual baggage.** Anyone who knows `port.in` and `port.out` has to
  learn `port.spi` as well, and the boundary to `port.out` needs explaining — formally both are
  "outgoing", only the implementer differs.
- **The benefit stands or falls with the naming.** A `port.spi` interface named after the supplying
  context instead of after the need has merely reversed the dependency, not decoupled it: the name
  would still carry knowledge about the other context. `PosterAttributionProvider` is already
  borderline in this respect — "Poster" is vocabulary of the supplying context.
- **The cycle rule cannot check `shared`.** Inside `shared` everything stays unchecked, and a
  context that routes a dependency through `shared` still circumvents the rule. The rule protects
  against oversight, not against intent.
- **An interface alone is not decoupling.** If a context accumulates five `port.spi` entries, that is
  an indication that the context boundary is in the wrong place, not a success of this pattern.

## Alternatives Considered

**Move the interface to `shared`.**
The first attempt. Costs one file move, turns the rule green and changes nothing about the coupling.
Rejected because it takes the cycle out of sight instead of resolving it, and because this approach
turns `shared` into a catch-all by design.

**Take the flag out of `/api/me` and offer it as its own endpoint from `titlecatalog`.**
Conceptually the cleanest solution: `/api/me` today gathers data from several contexts, and that is
exactly where the dependency came from. Rejected for this step, because it changes the API contract
and entails frontend work plus an additional bootstrap request — disproportionate for a single
boolean flag. Remains the right answer should `MeDto` accumulate further foreign fields.

**Put the interface in `accountaccess/port/out` and exempt it individually in `ArchitectureTest`.**
Would have got by without a new concept, following the example of the exemption for
`ImdbEntry`/`WatchlistDate`. Rejected because it waters down the meaning of `port.out`: that is where
dependencies live that are nobody else's business, and one exemption per special case would have
devalued that statement bit by bit.

**Registration at runtime instead of an interface** (titlecatalog registers the value with
accountaccess).
Rejected as mutable global state with initialisation order as an additional risk — for a value that
an interface supplies statically and traceably.
