# 0010. Don't Default Optionals to `null` (Consume Them Functionally)

- **Date**: 2026-07-27
- **Status**: Accepted

## Context

`Optional` exists so that "no value" does **not** travel through the code as `null` again (or as a
thrown exception).
An `Optional` arises in many places — repository lookups (`findByImdbId`), source queries
(`PosterSource.findPosterPath` / `download`), domain helpers (`toAvailableServiceNames`), stream
operations (`findFirst`) — and the rule is meant to apply to **all** of them, regardless of where
they came from.

What triggered this ADR was a concrete violation in `PosterService`:

```java
TitlePoster row = repository.findByImdbId(imdbId).orElse(null);
if (row == null || (row.getPosterPath() == null && !isNegativeFresh(row, now))) {
    row = (row == null) ? repository.save(TitlePoster.of(...)) : refresh(...);
}
```

Two variants of the same underlying problem typically show up in codebases:

- **`.orElse(null)` plus a `null` branch.** The `Optional` is flattened right away and then
  branched on with `if (x == null)` — exactly what `Optional` is supposed to avoid.
  In `PosterService` this pattern additionally hid a concurrency bug: the "`null` → so create a new
  one" `save` was not race-safe, `title_poster.imdb_id` is `unique`, and two parallel first
  accesses (thumbnail + hover) ran into `Duplicate entry '…' for key 'imdb_id'` at commit time.
- **Unsafe extraction.** `optional.get()` without a check, or `isPresent()` + `get()`, throws when
  the value is absent instead of handling the case — the flip side of the same coin.

The codebase mostly gets this right already — `StreamInfoService.resolve` chains
`result.filter(…).map(…).orElseGet(() -> fetch(…))` without ever touching `null` or `get()`.

## Decision

**Every `Optional` — whatever its origin — is consumed functionally** (`map` / `flatMap` /
`filter` / `or` / `orElse(value)` / `orElseGet` / `ifPresent` / `orElseThrow`) and is **not**

- flattened into `null` via `.orElse(null)` in order to branch on it afterwards, nor
- extracted via an unchecked `.get()` / `isPresent()`+`get()` (use `orElseThrow(…)` instead when
  presence is an invariant — that makes the invariant explicit and gives a clear error message
  when it breaks).

Applied:

- `PosterService.resolve` rewritten functionally (following `StreamInfoService`):

  ```java
  final TitlePoster row = repository.findByImdbId(imdbId)
          .map(existing -> reDiscoverStaleNegative(existing, now))
          .orElseGet(() -> discover(imdbId, now));
  if (row.getPosterPath() == null) return Optional.empty();     // Negativ-Cache
  return cachedBytes(row, size).or(() -> downloadAndStore(row, size));
  ```

  This also exposes the find-or-create and makes the race safety explicit (retry through the
  bean's own proxy on `DataIntegrityViolationException`).
- `AggregateService.includedFrom`: `watchlistCatalog.findByImdb(...).get()` →
  `.orElseThrow(...)` with an invariant message.

**Delimitation (deliberately allowed):** An *intentionally nullable data value* is not a
violation — a nullable DTO field (`WatchlistDto.lastImportedAt == null` = "never imported",
`OverviewEntryDto.services == null` = "N/A", `QueryResult.languages == null`) or the persisted
negative-cache marker (`title_poster.poster_path == null` = "no poster").
What is forbidden is `null` as a **sentinel for "not found"** with a branch on it afterwards, not
a field whose domain value legitimately is "none".
`Optional` itself is **not** used as a field type or as a DTO/JSON type.

## Consequences

**Easier / better:**

- No more `null` in the control flow and no more unchecked extraction; the methods read as a
  pipeline.
  Consistent with `StreamInfoService`.
- The duplicate-key bug is fixed and the race handling is named rather than accidental.

**Harder / downsides:**

- **Not machine-enforced.** ArchUnit sees types and methods, not the expression `.orElse(null)` or
  `.get()` on an `Optional` return; the rule is a review convention (the ADRs record *why*, not
  *how*).
- The retry in `PosterService` needs a self-proxy (`ObjectProvider<PosterService>`), because a
  direct self-invocation would bypass the transactional proxy.

## Alternatives Considered

- **Limit the rule to repository Optionals:** rejected — the underlying problem does not depend on
  the origin; an `Optional` from a source or a stream deserves the same treatment.
- **Keep `.orElse(null)` / `.get()`:** rejected — the former brings `null` back into the control
  flow, the latter throws uncontrolled;
  and the former had even hidden the concurrency bug here.
- **Model DTO fields as `Optional`:** rejected — `Optional` as a field/JSON value is an
  anti-pattern and would break the existing JSON contract (`lastImportedAt: null`).
  A nullable value remains the right expression for "legitimately none".
