# 0009. Domain Values Instead of Primitive Types

- **Date**: 2026-07-25
- **Status**: Accepted
- **Update (2026-07-29):** The JPQL pitfall described below is fixed, no longer merely worked
  around.
  `ImdbId` is now `@Embeddable` instead of being mapped onto a basic column via
  `@Converter(autoApply = true)`; every entity field overrides the column name individually
  (`@Embedded @AttributeOverride(name = "value", column = @Column(name = "imdb_id"))`).
  The reason: for a basic-type projection (`select w.imdbId` with `@Converter`), Hibernate's JPQL
  translation raises an implicit constructor expression (`new ImdbId(...)`), which it does not do
  for `@Embeddable` types — those are a known composite type, not a DTO return value it has to
  guess at.
  Verified empirically: `select distinct w.imdbId from WatchlistEntry w` with return type
  `List<ImdbId>` now works directly, produces exactly the same SQL as the previous native query,
  and `ImdbIdConverter` (along with its two tests) is gone entirely.
  This is not a new pattern: `Price`/`Availability` in `streamingavailability` have been
  `@Embeddable` in precisely this form for some time.
  The only extra cost: each of the five entity fields (`WatchlistEntry`, `TitleMeta`,
  `TitlePoster`, `QueryMeta`, `QueryResultDB`) now needs two annotations instead of one —
  `autoApply = true` used to cover every field without repetition, and a future sixth field will
  have to bring its own `@Embedded`/`@AttributeOverride` line.

## Context

Domain concepts were passed through the whole stack as bare primitives:
the IMDb id as a `String`, the release year as an `int`, the date added as a `String`.
That has several downsides:

- **Scattered validation / rules.** The `tt\w+` format of the IMDb id was only checked in
  `ExportReader`;
  the "year 0 = not yet released" rule was spread across the DTO factories (`PaidEntryDto`).
- **Interchangeability.** Nothing stopped an arbitrary `String` (a service name, say) from being
  passed where an IMDb id was expected.
- **No home for domain logic.** The canonical URL, the year display, the date comparison lived as
  ad-hoc expressions in controllers/templates.

## Decision

For these three concepts, **value objects** are introduced — in the backend **and** in the Angular
client — without changing the existing JSON and DB contracts.

**Backend (Java records in the `domain` package):**

- `ImdbId(String value)` — validates `tt\w+` in the compact constructor.
- `ReleaseYear(int value)` — owns the rule `value == 0 → "Not yet released"` (`display()`).
- `WatchlistDate(String value)` — types the date added, is `Comparable` and offers
  `toLocalDate()`;
  keeps the raw ISO string so that string sorting stays chronological.

The contracts stay stable through three techniques:

- **Jackson** `@JsonValue` / `@JsonCreator` →
  serialised as a bare string or bare number (`imdbId` stays `"tt…"`, `year` stays a number,
  `added` stays a string).
- **JPA** `@Converter(autoApply = true)` per value object →
  the columns stay `varchar`/`integer`, the entities carry the value objects without field
  annotations.
- **Spring MVC** `Converter<String, ImdbId>` →
  `@RequestParam ImdbId` binds directly;
  an invalid value becomes a **400** (the validation lives in `ImdbId`).

**Frontend (TypeScript, `core/domain.ts`):** **branded types** (`ImdbId = string & {__brand}`,
`ReleaseYear = number & {__brand}`, `WatchlistDate = string & {__brand}`) plus smart constructors.
At runtime they are still the string/number values delivered by the JSON (no mapping, nothing
broken about `http.get<T>()`);
at compile time the brand prevents the mix-up.
The small bits of domain logic (`imdbUrl`, `releaseYearDisplay`) live here too.

**Deliberately NOT converted** (left primitive): `username`, `name`/`streamingServiceName`,
`languages` —
plain strings with no rules of their own and no risk of confusion.
`price` is already a value object (`Price`).
The date added was modelled as a **string-based** `WatchlistDate` (not a strict `LocalDate`)
because the source format is external;
strict parsing on import would otherwise silently discard rows with a deviating format.

## Consequences

**Easier / better:**

- Validation and domain rules per concept in **one** place;
  an `ImdbId` in hand is always well-formed.
- Type safety across the whole stack;
  a `String` can no longer slip through as an IMDb id by accident (hard in the backend, via the
  brand in the frontend).
- A home for domain logic (`imdbUrl`, `ReleaseYear.display`), with a side benefit:
  the catalogue and flatrate tables now show "Not yet released" instead of `0`.
- JSON and DB contracts unchanged (secured by the Jackson and JPA converters;
  the `@WebMvcTest` slices assert `imdbId:"tt…"`, `year:` number, `added:` string).

**Harder / downsides:**

- More boilerplate:
  one JPA converter per value object (plus a Spring converter for the IMDb id) and the Jackson
  annotations.
- **JPQL pitfall:** `select distinct w.imdbId` on a value-object field makes Spring Data generate
  a DTO constructor expression (`new ImdbId(...)`) and fail;
  solved by a native query on the raw column plus wrapping in a `default` method.
- **Object equality:** `!=` on the former `int` year became an identity comparison;
  corrected to `Objects.equals` in `WatchlistImportService.differs` (otherwise every re-import
  reports a change).
- Test churn:
  literals (`"tt1"`, `2020`, `"2020-01-01"`) have to be wrapped in value objects or smart
  constructors;
  in the frontend, some friction from the branded types in the fixtures.

## Alternatives Considered

- **Keep the primitives:** rejected —
  the scattered validation and the interchangeability are precisely what triggered this.
- **Frontend: real classes with runtime mapping** instead of branded types: rejected —
  would need a mapping from the parsed JSON to instances in every API service and would break the
  direct `http.get<T>()`;
  more code and runtime cost for the same compile-time benefit.
- **`added` as a strict `LocalDate`:** rejected —
  the external source format is not guaranteed to be ISO;
  strict parsing would lose rows on import.
  `WatchlistDate.toLocalDate()` offers the date semantics where they are needed, without that
  risk.
