# where-to-stream (w2s)

Manage lists of movies to watch and find **where to stream them**.

Each signed-in user imports their own [IMDb](https://www.imdb.com/) watchlist CSV export;
w2s scrapes [werstreamt.es](https://www.werstreamt.es/) for each title's streaming availability,
caches the results in the database (shared across users), and presents each user's list as
per-provider web pages (Netflix, Prime Video, Disney+, WOW, Google Play).

## Tech stack

- Java 25, Spring Boot 4.1 (Spring MVC, JSON API)
- **Spring Security**: form + HTTP Basic + optional Google OIDC login, DB-backed users with
  `USER`/`ADMIN` roles (see [Authentication & users](#authentication--users))
- **Angular 22** SPA (standalone, zoneless, signals; **Angular Material** M3 UI with
  self-hosted Roboto, per-user light/dark theme) served under `/app` — the only UI — talking to a
  JSON API under `/api`. The one server-rendered page left is the login page (the OIDC-ready auth
  entry).
- **Poster thumbnails** (small preview + hi-res on hover), scraped from **IMDb** by default or
  sourced from the **TMDB API** behind a feature flag, cached as BLOBs in the DB (see
  [Poster images](#poster-images))
- Spring Data JPA on H2 (default) or MariaDB, schema managed by **Liquibase** (XML changelogs)
- jsoup (HTML scraping), Apache Commons CSV (IMDb export parsing)
- MapStruct (entity ↔ persistence mapping), Lombok
- Build: Maven

## How it works

1. Sign in, open **My Watchlist** (`/app/#/watchlist`) and upload your IMDb watchlist CSV
   export. The import is a full sync of *your* list: new titles are added, changed titles
   updated, and titles missing from the upload removed.
2. `ExportReader` parses the uploaded CSV stream into `ImdbEntry` records (malformed rows are
   skipped and logged); `WatchlistImportService` persists them to the `watchlist_entry` table,
   scoped to your user id.
3. `WerStreamtEsApiClient` scrapes werstreamt.es per title. Lookups are cached in the database
   (`StreamInfoService`) and considered stale after a configurable number of days. The cache is
   **global** (keyed by IMDb id, shared across users); outbound requests are rate-limited to stay
   polite.
4. The Angular SPA renders each user's aggregated availability per streaming service.

## Prerequisites

- **JDK 25** and **Maven**.
- **Node.js 22–24 + npm** (see `src/main/frontend/.nvmrc` / the `engines` field; `.npmrc` has
  `engine-strict=true`, so a mismatching version fails fast). The Maven build shells out to the
  system `npm` to build the Angular client. Only needed for a full build — use
  `-Dskip.frontend=true` for a backend-only build.

Ubuntu's `apt install nodejs npm` ships a Node too old for this project. Install a supported
version one of these ways:

```bash
# Option A — nvm (reads .nvmrc):
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash
exec "$SHELL"
cd src/main/frontend && nvm install    # picks up .nvmrc (Node 24); `nvm use` in later sessions

# Option B — NodeSource apt repo (Node 24 system-wide):
curl -fsSL https://deb.nodesource.com/setup_24.x | sudo -E bash -
sudo apt-get install -y nodejs         # includes npm

# verify
node --version   # v24.x (v22–v24 accepted)
npm --version
```

## Running locally

```bash
# run the app (defaults to http://localhost:8001)
mvn spring-boot:run

# run the tests
mvn test
```

On first start the database is empty; sign in and upload an IMDb CSV export under
**My Watchlist** (`/watchlist`) to populate your list.

`mvn spring-boot:run` (and `mvn package`) also builds the Angular client and folds it into the
same jar, so once the app is up the SPA is available at `http://localhost:8001/app/` (the root
`/` redirects there). Pass `-Dskip.frontend=true` for a backend-only build (skips the `npm`
steps).

### Architecture

Controllers hold no business logic: it lives in view-agnostic **application services**
(`tech.dobler.werstreamt.application`) that return DTOs. The `@RestController`s under
`tech.dobler.werstreamt.api` expose them as JSON under `/api`, which the Angular SPA consumes.
The Angular app (`src/main/frontend`) follows a smart/dumb split: container components under
`features/` own all data loading; presentational components under `shared/` only render inputs
(the availability tables are sortable by name / year / added date).

Domain concepts are modelled as **value objects** rather than bare primitives (`ImdbId`,
`ReleaseYear`, `WatchlistDate`; see [ADR 0009](docs/adr/0009-domainvalues-statt-primitiven.md)):
the backend keeps the JSON/DB contracts unchanged via Jackson `@JsonValue` + JPA `@Converter`, and
the Angular client mirrors them as branded types.

### Frontend development

For a fast edit/reload loop, run the backend and the Angular dev server separately:

```bash
mvn spring-boot:run -Dskip.frontend=true            # backend on :8001
cd src/main/frontend && npm start                   # ng serve on :4200, proxies /api -> :8001
```

Frontend unit tests run on **vitest** (via `@angular/build:unit-test`):

```bash
cd src/main/frontend
npm test            # watch mode
npm run test:ci     # single run (CI)
npm run test:coverage  # single run + v8 coverage report
```

## Test coverage

- **Backend** — JaCoCo (method & branch), report at `target/site/jacoco/` after `mvn test`
  (the network-only `ImdbApiClientTest` is excluded by default).
- **Angular** — Vitest v8 (`npm run test:coverage` in `src/main/frontend`).

The reads of "now" go through a `TimeService` facade (backend and frontend) instead of
`Instant.now()` / `Date.now()`, so time-dependent tests use a fixed clock — see
[`docs/adr/0003`](docs/adr/0003-zeit-ueber-timeservice-facade.md). This is **enforced**: the
backend `ArchitectureTest` (ArchUnit) checks both the layering and the no-`now()` rule during
`mvn test`; the Angular client enforces the no-`now()` rule via ESLint (`cd src/main/frontend &&
npm run lint`). Known architecture exceptions are tracked in [`TODOs.md`](./TODOs.md) (ARCH-1). Testing conventions are
recorded in [`docs/adr/0004`](docs/adr/0004-vitest-als-angular-test-runner.md) (Vitest) and
[`docs/adr/0005`](docs/adr/0005-assertj-und-mockito-im-backend.md) (AssertJ + Mockito).

## Running with Docker

The image builds everything inside the builder stage — JDK, Maven and a **pinned Node**
(copied from `node:24-alpine`) — so no host Node is needed for the Docker build. Host build
artifacts are kept out of the build context via `.dockerignore` (notably
`src/main/frontend/node_modules`): they are platform-specific and would otherwise break the
Alpine/musl build (a host `node_modules` from glibc is missing `@rollup/rollup-linux-x64-musl`);
`npm ci` runs fresh in the image instead.

The image builds the jar and runs it (see `Dockerfile` / `compose.yml`). `compose.yml`
mounts `./logs`, runs on port `8080`, and serves under the context
path `/w2s` on an external `webserver` network. It also starts a bundled `mariadb` service
(activated via the `mariadb` Spring profile) whose data lives in the `mariadb-data` **named
volume** — a named volume (not a host bind mount) so the database directory gets the right
ownership under rootless Podman/Docker and SELinux without manual `chown`/relabeling.

```bash
DOCKER_IMAGE_TAG=local docker build . --build-arg DOCKER_IMAGE_TAG=local -t w2s:local
DOCKER_IMAGE_TAG=local docker compose up -d
```

### MariaDB data: named volume vs. host bind mount (rootless Podman)

`compose.yml` stores the MariaDB data in the **`mariadb-data` named volume**. This is the
robust default: the engine creates the volume and initialises it with the image's
`mysql:mysql` ownership (including the correct user-namespace uid mapping), so it works
out of the box under rootless Podman + SELinux. Since the DB is a regenerable cache, the
storage location doesn't matter — prefer the named volume unless you specifically want the
files visible on the host.

If you *do* want a **host bind mount** (e.g. to inspect the data files directly), it needs
one manual step under rootless Podman. The reason: rootless containers run in a user
namespace where your host uid maps to container *root*, while `mariadbd` runs as the
container's `mysql` user (a different uid that maps to one of your *subuids*). A freshly
created host directory is owned by you (= container root), so the `mysql` process can't
write to it → `Can't create test file … (Errcode: 13 "Permission denied")`. The `:Z`
suffix only fixes SELinux labels, and `:U` chowns to the container's *declared* user
(root here — the image only drops to `mysql` later at runtime), so neither solves it.

The fix is to chown the host directory to the uid the container sees as `mysql`, from
*inside* the same user namespace via `podman unshare`:

```bash
# 1. find the uid this image uses for mysql (UBI variants differ from the Debian 999):
docker run --rm mariadb:lts-ubi id mysql        # -> uid=NNN(mysql) gid=NNN(mysql)

# 2. create the dir and chown it to that uid *within the user namespace*:
mkdir -p mariadb-data
podman unshare chown -R NNN:NNN mariadb-data    # NNN from step 1
```

Then point the `db` service at the bind mount (keeping `:Z` for SELinux):

```yaml
    volumes:
      - ./mariadb-data:/var/lib/mysql:Z
```

`podman unshare` enters the container's user namespace, so `chown NNN` there sets the host
subuid that the container sees as `mysql`. Re-run the `podman unshare chown` whenever you
recreate the directory.

The helper scripts `update-and-restart.sh` (pull + rebuild + restart) and
`upgrade-spring-boot.sh` (bump the Spring Boot parent, test, push) are intended to run on
the host, driven by `cron.sh`.

## Authentication & users

The app requires a login. Users live in the database with `USER` / `ADMIN` roles; read pages and
`GET /api/**` need any authenticated user, while state-changing / maintenance endpoints and user
administration need `ADMIN`. Details and rationale: [ADR-0006](docs/adr/0006-authentifizierung-und-autorisierung.md).

- **Login:** form login and HTTP Basic (e.g. `curl -u admin:… http://localhost:8001/check-pre-cache`).
- **Staying signed in:** HTTP sessions are persisted in the database (Spring Session JDBC), so a
  redeploy/restart no longer logs everyone out; they still time out after
  `server.servlet.session.timeout` (default 30m). Tick **"Stay signed in"** for a login that also
  survives closing the browser, and set a stable `w2s.security.remember-me.key`
  (env `W2S_SECURITY_REMEMBER_ME_KEY`) so remember-me tokens stay valid across restarts.

Deployment config (docker compose) is documented in [`.env.example`](.env.example) — copy it to
`.env` and fill in the secrets.
- **Initial admin:** on an empty user table an `admin` account is seeded. Set its password with
  `w2s.security.initial-admin.password` (env `W2S_SECURITY_INITIAL_ADMIN_PASSWORD`); if unset, a
  strong password is generated and logged once at startup — change it after first login.
- **User management:** `ADMIN`s manage users in the Angular UI (`/app/#/admin/users`), which
  calls `/api/admin/users`.
- **Per-user theme:** each account stores a UI colour-scheme preference (`SYSTEM`/`LIGHT`/`DARK`,
  default `SYSTEM` = follow the OS), chosen in the navbar and persisted via `PUT /api/me/theme`.
- **Google login (optional):** start with `SPRING_PROFILES_ACTIVE=google` and provide
  `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` (redirect URI `{baseUrl}/login/oauth2/code/google`).
  Without the profile, OIDC is off and only local accounts are used. First OIDC login provisions a
  local `USER` keyed by e-mail.

## Poster images

Each title shows a small poster thumbnail next to its name, and a high-resolution poster on hover.
The image **source** is pluggable (`PosterSource`): the app resolves a title's poster reference once,
downloads the two sizes **pre-sized from the source's CDN** (no server-side image processing), and
caches both as BLOBs in the DB (per `imdbId`, shared across users) so the source is hit at most once
per title. The browser then caches each image (long, immutable `Cache-Control` + `ETag`).

- **IMDb (default, no key):** the poster URL is looked up via IMDb's public **GraphQL API**
  (`title(id).primaryImage.url`, an Amazon image-CDN URL); the CDN resizes and re-compresses on the
  fly via URL params, so the row thumbnail is small and low-quality
  (`imdb-poster.thumb-width`/`thumb-quality`) and the hover image larger (`imdb-poster.full-*`).
  Lookups are throttled **conservatively** — `imdb-poster.rate-limit.requests-per-second` (default
  **2**). (HTML scraping of the title page does not work server-side: `www.imdb.com` returns an
  empty `202` to datacenter IPs.) **Note:** the API returns IMDb data under their terms (limited
  non-commercial use); this covers a personal watchlist, but TMDB below is the unambiguous path.
- **TMDB (opt-in):** set `TMDB_ENABLED=true` **and** a free v3 API key (`TMDB_API_KEY` / `tmdb.api-key`,
  from https://www.themoviedb.org/settings/api) to source posters from
  [The Movie Database](https://www.themoviedb.org/) instead (via its `find` endpoint + image CDN).
  With the flag or key missing, IMDb stays the source.
- Thumbnails are cached on first view and can be bulk-warmed via the ADMIN pre-cache (`POST
  /api/cache`); the hi-res image is fetched on first hover. A title with no poster is negatively
  cached (`poster.negative-cache-days`, default 14).
- **Attribution:** when **TMDB** is the active source the UI shows the TMDB logo and the required
  notice ("This product uses the TMDB API but is not endorsed or certified by TMDB."); with IMDb no
  footer is shown.

## Configuration

Key properties (`src/main/resources/application.properties`):

| Property | Default | Description |
| --- | --- | --- |
| `server.port` | `8001` | HTTP port (Docker overrides to `8080`) |
| `wer-streamt.invalidate.after-days` | `28` | Days before a cached lookup is refetched |
| `wer-streamt.rate-limit.requests-per-second` | `2` | Outbound throttle for werstreamt.es (`<= 0` disables) |
| `imdb-poster.rate-limit.requests-per-second` | `2` | Outbound throttle for the IMDb poster scraper (`<= 0` disables) |
| `poster.negative-cache-days` | `14` | How long a "no poster" result is cached before re-checking |
| `tmdb.enabled` | `false` | Use TMDB (not IMDb) as the poster source; also needs `tmdb.api-key` |
| `tmdb.api-key` | _(blank)_ | TMDB v3 API key (required when `tmdb.enabled=true`) |
| `spring.jpa.hibernate.ddl-auto` | `none` | Schema is owned by Liquibase (single source of truth) |

### Database & schema

The database holds the user accounts, their per-user watchlists, persistent HTTP sessions, and
the **global cached scrape results**. The schema is created and versioned by **Liquibase** as
portable XML changelogs (`src/main/resources/db/changelog/`), so the same changelog provisions
both H2 and MariaDB. Hibernate neither creates nor validates the schema
(`ddl-auto=none`); correctness is covered by the repository tests, which run on H2 and (via
Testcontainers) on a real MariaDB. The baseline assumes a fresh database — for an existing
deployment, drop the old data before the first Liquibase run; the cache repopulates via
`/pre-cache`.

**H2 (default):** file-based at `./db/demo`, used for local dev and in-memory tests.

**MariaDB (first-class):** activate the `mariadb` Spring profile and point it at your server:

```bash
SPRING_PROFILES_ACTIVE=mariadb \
  MARIADB_URL=jdbc:mariadb://localhost:3306/w2s MARIADB_USER=w2s MARIADB_PASSWORD=… \
  mvn spring-boot:run
```

`compose.yml` already wires the `w2s` service to a bundled `mariadb` service via this profile.

**Testcontainers MariaDB tests:** the repository suite also runs against a real MariaDB. These
are tagged `testcontainers` and excluded from the normal build (they need a container runtime
and image-pull access); run them with:

```bash
mvn -Ptestcontainers test
```

## Endpoints

**Angular SPA:**

| Path | Description |
| --- | --- |
| `/app/` | Single-page client (hash-routed: `/app/#/`, `/app/#/provider/netflix`, `/app/#/manage`, …) |

**JSON API (`/api`, consumed by the SPA):**

| Method & Path | Description |
| --- | --- |
| `GET /api/catalog` | All entries with their available services |
| `GET /api/providers/{amazon\|disney\|netflix\|wow\|google}` | Per-provider included + paid titles |
| `GET /api/watchlist` · `POST /api/watchlist/import` · `DELETE /api/watchlist` | Your watchlist: status / CSV import / clear |
| `PUT /api/watchlist/{imdbId}/seen` | Mark one of your titles seen / not seen (`{ "seen": true }`) |
| `GET /api/titles/{imdbId}/poster` · `…/poster/full` | Cached poster thumbnail / hi-res image (404 if none) |
| `GET /api/manage` · `POST /api/manage/invalidate` · `POST /api/manage/scrape` | Cache management (ADMIN) |
| `POST /api/cache` · `GET /api/cache/uncached` | Pre-cache all / count uncached (ADMIN) |
| `POST /api/refresh?scope=seen\|all` | Force-refresh cached results (ADMIN) |
| `GET /api/search?imdbId=…` | Resolve availability for a title |
| `GET /api/me` | The current principal (username, roles, admin flag, theme) |
| `PUT /api/me/theme` | Set the current user's theme (`SYSTEM`/`LIGHT`/`DARK`) |
| `GET /api/admin/users` · `POST` · `PUT`/`DELETE …/{id}` | User administration (ADMIN) |
| `GET /api/status` | Version & server start time (authenticated) |

**Server-rendered / public:**

| Path | Description |
| --- | --- |
| `/` | Redirects to `/app/` |
| `/login` (GET) / `/logout` (POST) | Login page (form + optional Google) and logout |
| `/public/status` | Version & server start time, as JSON — public health probe |

## Project status

This is a personal project. Known issues and planned improvements are tracked in
[`TODOs.md`](./TODOs.md).
