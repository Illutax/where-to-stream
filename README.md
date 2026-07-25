# where-to-stream (w2s)

Manage lists of movies to watch and find **where to stream them**.

Each signed-in user imports their own [IMDb](https://www.imdb.com/) watchlist CSV export;
w2s scrapes [werstreamt.es](https://www.werstreamt.es/) for each title's streaming availability,
caches the results in the database (shared across users), and presents each user's list as
per-provider web pages (Netflix, Prime Video, Disney+, WOW, Google Play).

## Tech stack

- Java 25, Spring Boot 4.1 (Spring MVC + Thymeleaf)
- **Spring Security**: form + HTTP Basic + optional Google OIDC login, DB-backed users with
  `USER`/`ADMIN` roles (see [Authentication & users](#authentication--users))
- **Angular 22** SPA (standalone, zoneless, signals; **Angular Material** M3 UI with
  self-hosted Roboto) served under `/app`, sharing the same server logic as the Thymeleaf UI
  via a JSON API under `/api`
- Spring Data JPA on H2 (default) or MariaDB, schema managed by **Liquibase** (XML changelogs)
- jsoup (HTML scraping), Apache Commons CSV (IMDb export parsing)
- MapStruct (entity ↔ persistence mapping), Lombok
- Build: Maven

## How it works

1. Sign in, open **My Watchlist** (`/watchlist`, or `/app/#/watchlist` in the SPA) and upload
   your IMDb watchlist CSV export. The import is a full sync of *your* list: new titles are
   added, changed titles updated, and titles missing from the upload removed.
2. `ExportReader` parses the uploaded CSV stream into `ImdbEntry` records (malformed rows are
   skipped and logged); `WatchlistImportService` persists them to the `watchlist_entry` table,
   scoped to your user id.
3. `WerStreamtEsApiClient` scrapes werstreamt.es per title. Lookups are cached in the database
   (`StreamInfoService`) and considered stale after a configurable number of days. The cache is
   **global** (keyed by IMDb id, shared across users); outbound requests are rate-limited to stay
   polite.
4. Thymeleaf pages (and the Angular SPA) render each user's aggregated availability per
   streaming service.

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
same jar, so once the app is up the SPA is available at `http://localhost:8001/app/` and the
Thymeleaf UI at `http://localhost:8001/`. Pass `-Dskip.frontend=true` for a backend-only build
(skips the `npm` steps).

### Architecture

Controllers hold no business logic: it lives in view-agnostic **application services**
(`tech.dobler.werstreamt.application`) that return DTOs. Both UIs call the same services — the
Thymeleaf `@Controller`s render them into templates, and the `@RestController`s under
`tech.dobler.werstreamt.api` expose them as JSON under `/api` for the Angular client. The
Angular app (`src/main/frontend`) follows a smart/dumb split: container components under
`features/` own all data loading; presentational components under `shared/` only render inputs.

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
- **User management:** `ADMIN`s manage users at `/admin/users` (Thymeleaf) or in the Angular UI
  (`/app/#/admin/users`); both call `/api/admin/users`.
- **Google login (optional):** start with `SPRING_PROFILES_ACTIVE=google` and provide
  `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` (redirect URI `{baseUrl}/login/oauth2/code/google`).
  Without the profile, OIDC is off and only local accounts are used. First OIDC login provisions a
  local `USER` keyed by e-mail.

## Configuration

Key properties (`src/main/resources/application.properties`):

| Property | Default | Description |
| --- | --- | --- |
| `server.port` | `8001` | HTTP port (Docker overrides to `8080`) |
| `wer-streamt.invalidate.after-days` | `28` | Days before a cached lookup is refetched |
| `wer-streamt.rate-limit.requests-per-second` | `2` | Outbound throttle for werstreamt.es (`<= 0` disables) |
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
| `GET /api/manage` · `POST /api/manage/invalidate` · `POST /api/manage/scrape` | Cache management (ADMIN) |
| `POST /api/cache` · `GET /api/cache/uncached` | Pre-cache all / count uncached (ADMIN) |
| `POST /api/refresh?scope=seen\|all` | Force-refresh cached results (ADMIN) |
| `GET /api/search?imdbId=…` | Resolve availability for a title |
| `GET /api/status` | Version & server start time |

**Pages (Thymeleaf):**

| Path | Description |
| --- | --- |
| `/` | All entries with their available services |
| `/amazon` (`/prime`) | Prime Video: included + paid |
| `/disney`, `/netflix`, `/wow` | Flatrate titles for that service |
| `/google` | Google Play (paid) titles |
| `/watchlist` (GET) / `/watchlist/import` (POST) / `/watchlist/clear` (POST) | Your watchlist: view / import a CSV / clear |
| `/public/status` | Version & server start time |

**REST / maintenance:**

| Path | Description |
| --- | --- |
| `/pre-cache` | Resolve & cache every title (across all users' watchlists) |
| `/check-pre-cache` | List titles without a cached result |
| `/refresh/all`, `/refresh/seen` | Force-refresh cached results |

> Note: the maintenance endpoints are currently unauthenticated `GET`s with side effects —
> see `TODOs.md` (TODO-5).

## Project status

This is a personal project. Known issues and planned improvements are tracked in
[`TODOs.md`](./TODOs.md).
