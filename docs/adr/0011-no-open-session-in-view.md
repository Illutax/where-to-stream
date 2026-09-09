# 0011. No Open Session in View, No Lazy Loading

- **Date**: 2026-07-27
- **Status**: Accepted

## Context

Spring Boot enables **Open Session in View (OSIV)** by default
(`spring.jpa.open-in-view=true`) and warns at startup that the setting should be set explicitly.
OSIV binds an `EntityManager` to the thread for the **entire** duration of the request (until the
response has been rendered),
so that lazy associations can be loaded outside the service layer too (during rendering, for
instance) without a `LazyInitializationException`.

That does not fit this application:

- Since ADR-0008 the UI is a **SPA with a JSON API** — there is no server-side view rendering,
  controllers return **DTOs**, never entities.
  So JSON serialisation never touches the persistence context.
- All associations are already **`FetchType.EAGER`** (`AppUser` roles, `QueryResultDB`,
  `QueryMeta.getQueries()`) — there is nothing left to load lazily.
- OSIV keeps the persistence context (and potentially a DB connection) attached to the request for
  longer than necessary.
  Exactly this kind of "hold a connection for the whole request" is what had just exhausted the
  Hikari pool when `PosterService` did network I/O inside a transaction — we deliberately want to
  keep DB work **within the service/transaction boundaries**.

## Decision

**`spring.jpa.open-in-view=false`** (set explicitly).

Along with it, the design rule: **no lazy loading.**
Associations are `EAGER` (or are loaded explicitly via a fetch join in the query); every DB access
happens inside a `@Transactional` service method; entities are **not** passed across the layer
boundary or handed to serialisation — the presentation layer only ever sees DTOs.
Whatever a response needs is loaded completely in the service layer and handed out as a DTO.

## Consequences

**Easier / better:**

- DB connections are held only inside the transactions, not for the whole request —
  consistent with the connection discipline from the poster fix.
- **Fail-fast:** if someone later adds a `LAZY` association and serialises it outside a
  transaction, there is an immediate, clear `LazyInitializationException` instead of a silent
  extra access during rendering.
- The startup warning is gone; the decision is documented rather than inherited.

**Harder / downsides:**

- In the service layer you have to **load everything deliberately** that the DTO needs (no
  convenient loading "later on").
  With the current, small EAGER collections this is not a problem.
- `EAGER` everywhere always loads the whole association.
  As long as the collections stay small (roles, query results per title) that is fine;
  if an association grows substantially, the right answer is a targeted fetch join in the
  repository — **not** switching OSIV back on.

## Alternatives Considered

- **Leave OSIV on (`true`) and just silence the warning:** rejected — it buys nothing for a
  DTO-based JSON API, keeps the persistence context around longer, and hides accidental DB access
  outside transactions.
- **Introduce lazy loading and rely on OSIV:** rejected — it couples loading to rendering and
  passes entity proxies all the way into serialisation; we want the loading logic explicitly in
  the service layer.
  If a collection grows, it gets loaded with a targeted fetch join.
