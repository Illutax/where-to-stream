# 0006. Authentication & authorization (Spring Security)

- **Date**: 2026-07-22
- **Status**: Accepted
- **Update (2026-07-25):** The Thymeleaf client was removed
  ([ADR 0008](0008-remove-the-thymeleaf-client.md)).
  The "both clients" aspects described here (Thymeleaf forms,
  the `/admin/users` page, user administration in both clients) therefore apply to the Angular SPA
  only;
  the login page remains as the sole server-rendered auth entry point.
  The remaining GET-with-side-effect maintenance endpoints were removed (closing TODO-5
  completely),
  and `SecurityConfig` was simplified to the SPA-only model.

## Context

w2s had no authentication at all;
the state-changing maintenance endpoints were unauthenticated GETs (see TODO-5).
Both clients (the Thymeleaf UI and the Angular SPA) are to be secured, with local
user administration and optional SSO login.

## Decision

Spring Security 7 (Boot 4) with a **database-backed user store**:

- **Login paths:** form login **and** HTTP Basic over local accounts (BCrypt via
  `DelegatingPasswordEncoder`), plus an **optional Google OIDC login** (active only when a
  client registration is configured —
  profile `google` + `GOOGLE_CLIENT_ID/SECRET`; without that, the app runs on local accounts).
  OIDC logins are mapped onto local accounts by e-mail address and provisioned as `USER` on first
  login.
- **Roles:** `USER`, `ADMIN`.
  **Access model:** everything requires a login;
  state-changing / maintenance endpoints (pre-cache, refresh, invalidate, scrape, list-change) and
  user administration require `ADMIN` — this fixes TODO-5.
  Method security (`@PreAuthorize`) on `UserAdminService` as defense in depth.
- **User store:** `AppUser` + roles (Liquibase changelog 003), `AppUserDetailsService`;
  an initial admin is seeded when the user table is empty (password from
  `w2s.security.initial-admin.password`, otherwise generated and logged once).
- **CSRF:** cookie-based (`CookieCsrfTokenRepository`) with an SPA handler + cookie filter.
  The Angular API base is a **relative** URL (`../api/`), so that Angular's built-in
  XSRF interceptor sets the `X-XSRF-TOKEN` header.
- **API vs. pages:** `/api/**` answers unauthenticated requests with **401** (the SPA then navigates
  to the login page), page requests in the browser are **redirected** to `/login`.
- **Sessions:** **persisted in the database** (Spring Session JDBC, `spring-boot-session-jdbc`; schema
  via Liquibase 005, `initialize-schema=never`) →
  sessions **survive restarts** (the deployment rebuilds/restarts via cron).
  Idle timeout `server.servlet.session.timeout` (default 30 min).
  Additionally **remember-me** ("Stay signed in") with a stable key (`w2s.security.remember-me.key`)
  for a persistent login that outlives closing the browser;
  if no key is set, a transient one is generated (warning in the log).
- **User administration** in **both** clients:
  Thymeleaf (`/admin/users`) and Angular (`/admin/users`, route guard), both through the same
  `UserAdminService`/`/api/admin/users`.

## Consequences

**Easier / better:**

- TODO-5 solved: no unprotected mutating endpoints any more.
- One user/role model for both clients and both login paths; SSO without forcing it.
- Protection against locking yourself out:
  the last enabled admin cannot be demoted/disabled/deleted.

**Harder / drawbacks:**

- The Google OIDC flow cannot be tested end to end in the sandbox (no real IdP);
  what is covered is the configuration + the mapping logic, not the live redirect.
- Some of the maintenance endpoints remain GET-with-side-effect (now ADMIN-protected);
  the proper verb correction (POST) is still open (the remainder of TODO-5).
- The application layer is now allowed to use repositories (`UserAdminService`) —
  the ArchUnit layering rule was relaxed accordingly (repositories are the port of the
  use-case layer).

## Alternatives Considered

- **Resource server (JWT bearer) instead of session login:** rejected —
  the app is a session-based web app with a server-rendered login;
  bearer tokens would have required a token issuer/flow with no benefit for the internal UI.
- **OIDC only (no local store):** rejected —
  local user administration is wanted, without forcing an external IdP.
- **In-memory users:** rejected — user administration needs persistence.
