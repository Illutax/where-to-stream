# 0001. Hash routing for the Angular client

- **Date**: 2026-07-19
- **Status**: Accepted
- **Update (2026-07-25):** The Thymeleaf UI was removed
  ([ADR 0008](0008-remove-the-thymeleaf-client.md)); the SPA is the only UI.
  Parts of the context below are therefore historical: the Thymeleaf paths listed there (`/`,
  `/amazon`, `/list`, `/manage`, …) no longer exist, and the argument "zero risk of collision with
  the Thymeleaf routes" is moot.
  The **decision stands**: hash routing was deliberately kept — what carries it now is environment
  portability (one build for dev/JAR/PROD context path) and the `document.baseURI` → `../api/`
  trick.
  `/` no longer serves a page; it redirects to `/app/`.
  The trigger "the Thymeleaf UI is replaced", named below, has therefore **occurred, but without a
  switch** — the actual occasion to revisit path routing remains an SPA redirect OIDC login
  (Keycloak, see [ADR 0008](0008-remove-the-thymeleaf-client.md)) or the app going public.

## Context

With the restructuring of w2s, an Angular client (SPA) has joined the existing Thymeleaf UI.
Both clients run out of **one** Spring Boot fat JAR:

- The Thymeleaf UI occupies the existing server paths (`/`, `/amazon`, `/prime`, `/disney`,
  `/netflix`, `/wow`, `/google`, `/list`, `/manage`, `/public/status`).
- The SPA is served under `/app/`; its bundle sits in the JAR under `static/app/`.
- In PROD the application runs behind the context path `/w2s` (`compose.yml` sets
  `server.servlet.context-path=/w2s`);
  locally (JAR or `mvn spring-boot:run`) without a context path on port 8080;
  in frontend dev via `ng serve` on port 4200 with an `/api` proxy.

The Angular router therefore has to work in **three** runtime environments with different base
paths, without producing a separate build artefact per environment.
The SPA is an internal personal app — SEO, public link shareability and crawler indexing are not
goals.

An additional design decision that depends on the routing choice: the API base URL is derived at
runtime from `document.baseURI` as `../api/` (`core/api-base.ts`), so that the same build addresses
the API correctly in dev, in a local JAR and in PROD.
This trick requires the document to **always** sit at exactly `<context>/app/`.

## Decision

The Angular router uses **hash-based routing** (`provideRouter(routes,
withHashLocation())` in `app.config.ts`), together with `baseHref: "./"` in `angular.json`.
SPA routes then look like this: `/w2s/app/#/provider/netflix`, `/app/#/manage`, and so on.

On the server side the minimal `SpaController` with two mappings suffices: `GET /app` → redirect
to `/app/`, `GET /app/` → forward to `/app/index.html`.
Spring Boot serves the static assets (`/app/*.js`, `/app/*.css`) automatically.

## Consequences

**Easier / better:**

- **No server-side deep-link fallback needed.**
  The `#/...` part never reaches the server; from the server's point of view every deep link is
  `GET /app/`.
  No catch-all handler, no asset exceptions.
- **Zero risk of collision** with the Thymeleaf routes — all the server sees of the SPA is `/app/`.
- **One build for all environments.**
  `baseHref: "./"` works in dev (`ng serve` at `/`), in a local JAR (`/app/`) and in PROD
  (`/w2s/app/`), without per-environment `--base-href` variants.
- **The `document.baseURI` trick stays trivially correct**: `baseURI` is always
  `<context>/app/`, `../api/` always resolves to `<context>/api/` — regardless of route depth.
- Robust behind arbitrary reverse proxies/context paths without rewrite rules.

**Harder / drawbacks:**

- URLs contain `#/` and look old-fashioned, or are harder to read "by hand".
- Fragment anchors (`#section` for scrolling) conceptually clash with the routing fragment.
- Fragments are handled poorly by some external systems: **OAuth2/OIDC redirect URIs must not
  contain a fragment**, and some mail/chat clients truncate `#...` when linking.
- The server never sees an SPA route → **no access log/monitoring per SPA page**.
- Analytics tools would need extra configuration for hash-change tracking.
  (Irrelevant for an internal app without analytics.)
- SEO: hash routes are second-class for crawlers. (Meaningless for an internal app.)

The decision is **reversible later without data loss** — URLs are not persisted data.
Switching to path routing means: remove `withHashLocation()`, add a fallback controller, set
`base href` absolutely per environment;
old `#/` links can be forwarded client-side by redirect.
So there is **no lock-in**.

## Alternatives Considered

**Path routing (`PathLocationStrategy` / History API, e.g. `/w2s/app/provider/netflix`).**

Pro: canonical, "pretty" URLs; the Angular default;
fragment anchors and `scrollPositionRestoration` without special cases;
real SPA paths in the server logs; SEO-friendly.

Contra (decisive against this option):

- **A server fallback is mandatory.**
  A catch-all `GET /app/**` → forward `index.html` has to exempt asset requests (`.js`, `.css`,
  fonts) correctly, otherwise `index.html` is served with `200` instead of a `404` for missing
  bundles (wrong MIME types, silent failures) —
  a classic never-ending source of bugs.
- **`baseHref: "./"` breaks.**
  On a deep link `/w2s/app/provider/netflix`, a relative `<base href="./">` would resolve to
  `/w2s/app/provider/` → asset requests go nowhere (404 on `main-*.js`).
  An absolute `base href` would be required (`/app/` locally, `/w2s/app/` in PROD) → either
  **two builds** (`--base-href` per environment) or server-side rewriting of `index.html` at
  runtime (an additional component).
- The `../api/` trick based on `document.baseURI` only keeps working if that absolute
  `base href` is set correctly —
  the runtime portability of the current setup (one artefact for all environments) would be lost,
  or would have to be bought back through index rewriting.

Assessment: the main arguments for path routing (SEO, analytics, URL aesthetics) are twice
irrelevant and once a matter of taste for an internal personal app.
The costs of path routing, by contrast, are concrete (two build variants or index rewriting +
an error-prone fallback) for zero functional gain.
Since the migration remains possible at any later point, the choice falls on hash routing.

**Decide differently if:** the app goes public (SEO/shareability starts to count), an OAuth2/OIDC
login with a redirect into the SPA is introduced (fragment URLs are problematic as a redirect URI),
server logs/monitoring per SPA route are needed, or the Thymeleaf UI is fully replaced by the SPA
and the SPA is to move to `/`.
Then switch to path routing with an absolute `base href` and a fallback controller in one go.
