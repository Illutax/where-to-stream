# 0008. Remove the Thymeleaf client — SPA-only UI

- **Date**: 2026-07-25
- **Status**: Accepted

## Context

w2s had two equivalent UIs on top of the same application layer:
the server-rendered Thymeleaf UI and the Angular SPA (ADR 0001).
Both were maintained in parallel for every feature change (most recently the per-user watchlist, ADR
0007) —
twice the effort, twice the test surface.
The Angular SPA is by now functionally complete (catalog, providers, watchlist import,
cache management, user administration, auth-aware navigation).
On top of that, an external OIDC provider (e.g. Keycloak) is planned down the line, where the login
sits with the IdP anyway —
a login dialog of our own would be throwaway code.

## Decision

The Thymeleaf UI is removed; the **Angular SPA is the only UI**.
The **login page** is kept as the only server-rendered page —
it is the auth entry point and is already OIDC-ready (the "Sign in with Google" button is the same
pattern as a future Keycloak button).

- **Removed:** the server-rendered application pages
  (catalog/providers/manage/admin/watchlist/status view) along with their `@Controller`s,
  `CommonAttributeService`, `ThymeleafConfig`, all templates except `login.html`,
  and the obsolete GET-with-side-effect maintenance endpoints (`/pre-cache`, `/check-pre-cache`,
  `/refresh/**`) —
  which finishes off the rest of TODO-5 (there are no mutating GETs left;
  maintenance runs exclusively through `POST /api/**`).
- **Kept/reworked:** `login.html` + `LoginController`;
  `StatusController` now serves `/public/status` as **JSON** (a public health probe), and the
  SPA reads the same state via `/api/status`;
  `SpaController` redirects the root `/` to `/app/`.
- **SecurityConfig simplified** to the SPA-only model:
  public are only `/login` (plus its Bootstrap CSS via `/webjars/**`) and `/public/**`;
  `ADMIN` now only for `/api/admin/**`, `/api/manage/**`, `/api/cache/**` and `POST /api/refresh`;
  `/api/**` answers with **401**, all other (browser) requests are **redirected** to
  `/login`.
  The many legacy matchers (`/admin/**` pages, `/pre-cache`, `/manage`, `/css //js`, …) are gone.
- **Build slimmed down:** the Thymeleaf dialects (`thymeleaf-extras-springsecurity6`,
  `thymeleaf-layout-dialect`) and the webjars used only by the old navigation
  (jQuery/Popper/Font Awesome) are dropped;
  `spring-boot-starter-thymeleaf` and the Bootstrap webjar stay, for the login page alone.

## Consequences

**Easier / better:**

- Only one UI to maintain and test;
  no more "keep both clients in sync" (this corrects the "both clients" part of ADR 0006 and
  0007).
- A much smaller, more comprehensible `SecurityConfig`;
  TODO-5 fully closed (no mutating GETs left).
- A smaller attack and dependency surface (fewer templates, fewer webjars).

**Harder / drawbacks:**

- Without JavaScript enabled there is no UI any more (the SPA is mandatory).
- Server-side authorization cannot distinguish individual SPA routes:
  because of hash routing (ADR 0001), every SPA navigation arrives as `GET /app/`.
  Only the **SPA shell as a whole** can be secured (`/app/**` requires a login);
  fine-grained authorization happens client-side (route guard) plus server-side per `/api/**` endpoint.
  The only publicly reachable state is the `/public/status` health probe.
- The login page stays a Thymeleaf template for now;
  it goes away (or becomes a plain redirect) as soon as Keycloak/OIDC takes over the login flow.

## Alternatives Considered

- **Move the login into the SPA (an Angular login form of our own):** rejected —
  with OIDC/Keycloak coming up, a self-built form would be throwaway code;
  the existing login page is the better, IdP-ready stepping stone.
- **Make the SPA fully public and secure only `/api/**`:** rejected for the interim —
  it would need HTTP Basic as the only login path (worse UX) instead of the form login;
  the model we want is "everything behind a login, redirect to the (future) IdP".
- **Keep both UIs:** rejected —
  the doubled maintenance/test effort is what triggered this decision in the first place.
