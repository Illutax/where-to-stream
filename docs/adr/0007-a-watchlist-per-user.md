# 0007. Per-user watchlist (database-backed instead of a global file list)

- **Date**: 2026-07-25
- **Status**: Accepted
- **Update (2026-07-25):** The Thymeleaf client was removed right afterwards
  ([ADR 0008](0008-remove-the-thymeleaf-client.md));
  the Thymeleaf `/watchlist` page mentioned here is gone, and the watchlist upload now runs only via
  the Angular SPA (`/app/#/watchlist`) or `/api/watchlist`.

## Context

The watchlist used to be **global**:
`ImdbCatalog` loaded the lexicographically last CSV from the `assets/` directory into memory at
startup,
and a global "selected list" state (`ListSelectionService`, `/list` or
`PUT /api/lists/selection`) switched between the files.
All users saw the same list; switching was an ADMIN action.
With authentication now in place (ADR 0006),
**every user should maintain their own list**.

## Decision

The watchlist is kept **per user** in the database and maintained by upload.

- **Persistence:** new table `watchlist_entry` (Liquibase 006), key `(user_id, imdb_id)`, FK
  to `app_user` with `ON DELETE CASCADE`.
  `WatchlistEntry` is the JPA entity.
- **Layers:** `ImdbCatalog` (global, in-memory) is replaced by `WatchlistCatalog` (database,
  scoped by `userId`).
  All read/query methods in the services and application layers now take a
  `UUID userId`.
  The `SecurityContext`/the user name is read **only in the presentation layer**;
  `CurrentUserService` translates the authenticated user name into the `userId`, so that the
  layers below never see Spring Security.
- **Import:** instead of files in the `assets/` directory, the user uploads their IMDb CSV export
  through the UI (`/watchlist`, `/api/watchlist/import`, multipart).
  `ExportReader` parses an `InputStream` (no more file paths).
  `WatchlistImportService` performs a **full sync**:
  new titles are created, changed ones updated, ones missing from the upload removed.
  The import is deliberately a pure database operation (no synchronous scraping inside the
  transaction) —
  titles are resolved lazily against the cache on the first page view.
- **The global cache stays global:** the werstreamt.es cache (`query_meta`) is keyed per IMDb id
  and **shared across all users**.
  Pre-cache/refresh/manage work on the union of all watchlists (distinct `imdbId`);
  a title counts as "seen" once any user has rated it.
- **Authorization:** the watchlist is accessible to **every logged-in user**;
  the ADMIN restriction goes away for the (now removed) list endpoints.
  Cache maintenance stays ADMIN.
- **Both clients:** Thymeleaf (`/watchlist`) and Angular (`/app/#/watchlist`, `WatchlistImportPage`
  + `WatchlistApi` + `WatchlistStore`) offer upload/status/clear;
  the navigation shows "My list: N titles" instead of the selected list.
  The old artefacts (`change-list`, `list-picker`, `ListsApi`, `ListSelectionStore`) have been
  removed.

## Consequences

**Easier / better:**

- Genuine multi-user use:
  each person maintains their own list without overwriting anyone else's.
- No more `assets/` volume and no more filesystem state —
  the import runs through the authenticated UI;
  the `assets` mount is dropped from `compose.yml`.
- Isolation is enforced at the data level (all queries scoped by `userId`) and covered by repository
  tests (H2 + Testcontainers MariaDB).

**Harder / drawbacks:**

- An additional boundary `username → userId`:
  the presentation layer has to resolve the authenticated user and pass it down;
  forgetting to scope somewhere would be a data leak between users (covered by tests).
- The import is a full sync (it deletes titles missing from the upload) —
  that is intentional, but more destructive than a pure add;
  the UI points this out.
- Existing deployments lose the old global file list;
  users have to upload their CSV export once.

## Alternatives Considered

- **Keep the global list and just filter per user:** rejected —
  there is no sensible filter, everyone has a different list.
- **Keep uploads as files in the `assets/` directory (one subfolder per user):** rejected —
  filesystem state in the container is fragile with rootless Podman/SELinux and with the
  cron-restart deployment;
  the database is there anyway and is backed up.
- **Pre-cache the import synchronously:** rejected —
  it would block the upload for a long time and put werstreamt.es under load;
  lazy resolution on page view is enough.
