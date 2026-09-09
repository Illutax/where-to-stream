# 0015. Self-validating commands instead of scattered request validation

- **Date**: 2026-07-30
- **Status**: Accepted

## Context

Two patterns for "input into a use case" sat side by side, without it being clear which one
was the model to follow:

- **Unpacked primitives**: most services took individual values unpacked from the request —
  `WatchlistImportService.addOne(UUID userId, ImdbId imdbId, String name, ReleaseYear
  year)`, `UserPreferencesService.updateTheme(String username, Theme theme)`.
- **Whole request object**: `UserAdminService.create(CreateUserRequest request)`, by contrast, took the
  complete request object, unopened by the controller.

Neither of the two validated itself.
Validation was pure controller prose — the same block
(`if (request == null || request.feld() == null) throw new ValidationException(...)`) appeared in
nine places, almost word for word, without Bean Validation (`@Valid`/`@NotNull`), which is used
nowhere in the entire project.

With `UserAdminService`, the line between "structural validation" (empty username/
password) and "business rule" (duplicate username, last admin) was blurred on top of that: both
threw `UserManagementException`, even though only the latter is a genuine business rule (it needs
DB access) — the former is a pure form check that needs no repository access.

## Decision

**Wire `*Request` records stay dumb data carriers** (`adapter/in/api`, bound via `@RequestBody`,
no validation of their own) — that is unchanged.
**New `*Command` records** (`application/command/`) are what every application service method
now takes: **exactly one parameter per method**, bundling both the context (username, UserId,
ImdbId — from `Authentication`/`@PathVariable`) and the payload from the wire request,
and validating **itself in its own compact constructor** — exactly the pattern `ImdbId`
already uses for its `tt\w+` format, only here for mandatory fields/value ranges instead of formats.

```java
public record ThemeUpdateCommand(String username, Theme theme) {
    public ThemeUpdateCommand {
        if (theme == null) {
            throw new ValidationException("A theme is required.");
        }
    }
}
```

The controller constructs the command from the wire request plus the context and passes **only the
command** on to the service — no more loose parameters, no more manual checks in the
controller method body:

```java
public void updateTheme(Authentication authentication, @RequestBody ThemeUpdateRequest request) {
    userPreferencesService.updateTheme(new ThemeUpdateCommand(authentication.getName(), request.theme()));
}
```

**Where the wire request and the command would be identical** (no additional context fields needed, e.g.
`CreateUserCommand`, `InvalidateCommand`), the separate wire type is dropped — the command itself is
then bound directly via `@RequestBody`.

**Structural validation moves into the compact constructor, business rules stay in the
service.** For `UserAdminService` that meant a real clarification, not just a relocation:
an empty username/password now throws `ValidationException` (from `CreateUserCommand`'s own
constructor, no DB interaction needed), while a duplicate username and "last admin" still throw
`UserManagementException` in the service (both need a repository access, which a
record constructor must not perform). Likewise, `UsernameUpdateCommand`'s
availability check (previously in the controller: call `usernameAvailable()`, only then
`updateUsername()`) moved entirely into `UserPreferencesService.updateUsername` itself — the service
now fully owns its own business rule, instead of having the controller pre-check it
on its behalf.

**Verified, not assumed**: before command validation was experimentally moved directly into a
`@RequestBody`-bound type (the variant without a separate wire type), we checked
whether a `ValidationException` thrown in the compact constructor still reaches
`ApiExceptionHandler` at all when invoked through Jackson's record deserialisation.
It does: Spring unwraps the cause chain of `HttpMessageNotReadableException` and finds the
matching `@ExceptionHandler` — the status, the `application/problem+json` content type and the exact
`detail`/`title` message stay identical to the previous variant thrown in the controller.

## Consequences

**Easier / better:**

- Every service method now has exactly one signature shape (`method(XyzCommand command)`) instead of
  two competing styles.
- Validation lives in **one** place per field (in the command), not on a controller line
  that gets retyped for every new endpoint.
- `UserAdminService` lost its `requireText`/`roles` helper methods entirely — both checks
  now live directly in `CreateUserCommand`/`UpdateUserCommand`.
- `WatchlistEntryRepository`-style wrapper logic (two lines of "null → empty list") existed twice for
  `InvalidateRequest` (in the controller **and** the service) — now once, in
  `InvalidateCommand`'s compact constructor.

**Harder / drawbacks:**

- More files: seven preference commands still need their dumb wire requests alongside them
  (Jackson cannot read `username`/`userId` from the JSON body), making 14 instead of 7 types for the
  `/api/me/*` endpoints.
- Records cannot perform conditional type conversion in the compact constructor (e.g. `Integer`
  in, `ReleaseYear` out) without an additional, overloaded constructor — deliberately avoided
  (see the alternatives), which is why commands still carry raw wire types in places (`Integer year` instead of
  `ReleaseYear year`), with services doing the last conversion step themselves.

## Alternatives Considered

- **Full CQRS** (separate read/write models, possibly event sourcing): clearly
  oversized for this project — a single DB, no scaling pressure, no business need for
  event history. Rejected without closer examination.
- **Renaming `*Dto` towards "query result"**: DTOs are already pure output projections,
  never reused for input — a purely cosmetic rename with no structural gain.
  Rejected.
- **Give every command an additional overloaded constructor for wire-type conversion**
  (e.g. `CreateUserCommand(String, String, String, List<Role>)` plus a canonical variant with
  `Set<Role>`): tried for `CreateUserCommand`, then rejected — one additional
  constructor overload per command just for a single type conversion is more ceremony than
  it saves; the conversion stays a one-liner in the service.
- **Introducing a command where there is no need for one** (e.g. `ClearCommand(UUID userId)` for
  `WatchlistApiController.clear`): rejected — a wrapper around a single, always trusted
  value with nothing to validate would be pure ceremony, the same reasoning as the absence of a
  port for `ImdbTitleClient`/`ImdbSuggestionClient` in ADR-0014.
