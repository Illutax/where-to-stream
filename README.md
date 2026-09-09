# where-to-stream (w2s)

Manage lists of movies to watch and find **where to stream them**.

Each signed-in user imports their own [IMDb](https://www.imdb.com/) watchlist CSV export;
w2s scrapes [werstreamt.es](https://www.werstreamt.es/) for each title's streaming availability,
caches the results in the database (shared across users),
and presents each user's list as per-provider pages (Netflix, Prime Video, Disney+, WOW, YouTube Store).

It is a personal project, run as a single small deployment for a handful of users.

## What you get

- **Your watchlist, sorted by where you can actually watch it** — one page per provider,
  plus a dashboard over everything.
- **Poster thumbnails** next to every title, hi-res on hover.
- **FSK age-rating badges** (German rating, or a foreign certificate as fallback), switchable per user.
- **German titles**, optionally, instead of the original ones.
- **An eBay search link** per title — opens your chosen marketplace, filtered to discs and sorted
  by lowest total price. It is a link, not a price lookup: see
  [TODO-56 in `DONE.md`](DONE.md) for why the price lookup was built, used, and then withdrawn.
- **Cache management** for admins: see when each title was last scraped, invalidate, re-scrape.
- **Instance metrics** for admins: titles, posters, users and cache coverage.
- **Impersonation** for admins: act as another user to reproduce a report
  ([ADR-0020](docs/adr/0020-admin-impersonation-via-switchuserfilter.md)).

---

# Getting started

Two ways in. **Docker is the one production uses** and needs the least on your machine;
the local run is for developing.

## With Docker

Needs a container runtime (Docker or rootless Podman) and nothing else — JDK, Maven and a pinned
Node all live in the build image.

```bash
cp .env.example .env        # fill in the secrets
DOCKER_IMAGE_TAG=local docker build . --build-arg DOCKER_IMAGE_TAG=local -t w2s:local
DOCKER_IMAGE_TAG=local docker compose up -d
```

`compose.yml` runs the app under the context path `/w2s` (port `8080`, same as locally), on an external
`webserver` network, and starts a bundled **MariaDB** alongside it (Spring profile `mariadb`).
Its data lives in the `mariadb-data` **named volume** — not a host bind mount, so the directory
gets the right ownership under rootless Podman and SELinux without any manual `chown`.

Behind a reverse proxy, two settings have to line up with it; the comment next to
`server.servlet.context-path` in `compose.yml` says exactly which and why. Getting them wrong
fails *quietly* — the app keeps working and only its redirects point at the wrong scheme.

<details>
<summary>Host bind mount instead of the named volume (rootless Podman)</summary>

Prefer the named volume unless you need the files visible on the host. For a bind mount, first
chown the directory to the container's `mysql` uid **from inside its user namespace** — a plain
`chown` from the host does not reach the right uid mapping:

```bash
docker run --rm mariadb:lts-ubi id mysql        # find the mysql uid (NNN); UBI vs. Debian images differ
mkdir -p mariadb-data
podman unshare chown -R NNN:NNN mariadb-data    # NNN from the previous command
```

Then point the `db` service at it, keeping `:Z` for SELinux:

```yaml
    volumes:
      - ./mariadb-data:/var/lib/mysql:Z
```

Re-run the `podman unshare chown` whenever you recreate the directory.
</details>

## Locally

**Prerequisites:** JDK 25, Maven, and Node.js 24 (the current LTS) with npm.

The Maven build shells out to the system `npm` to build the Angular client.
Node 24 is not a recommendation but a requirement: `.npmrc` sets `engine-strict=true`, so a
different major fails `npm ci` outright instead of half-building.
[ADR-0021](docs/adr/0021-track-one-node-lts-major-checked-by-a-test.md) explains why it is one
major rather than a range, and `ToolchainVersionsAgreeTest` keeps `.nvmrc`, `engines` and the
Dockerfile from drifting apart.
Pass `-Dskip.frontend=true` for a backend-only build.

Ubuntu's `apt install nodejs npm` ships a Node too old for this project:

```bash
# Option A — nvm (reads .nvmrc):
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash
exec "$SHELL"
cd src/main/frontend && nvm install    # picks up .nvmrc (Node 24); `nvm use` in later sessions

# Option B — NodeSource apt repo (Node 24 system-wide):
curl -fsSL https://deb.nodesource.com/setup_24.x | sudo -E bash -
sudo apt-get install -y nodejs         # includes npm

node --version   # v24.x — nothing else is accepted
```

**Then pick a database.** Production runs on MariaDB; locally you have two options, and it is
worth knowing which one you are on:

```bash
# A — MariaDB, same as production. Needs a server, e.g. the bundled one:
docker compose up -d db
SPRING_PROFILES_ACTIVE=mariadb \
  MARIADB_URL=jdbc:mariadb://localhost:3306/w2s MARIADB_USER=w2s MARIADB_PASSWORD=… \
  mvn spring-boot:run

# B — H2, no setup at all. File-based at ./db/demo, so your data survives a restart:
mvn spring-boot:run
```

Option B is the default and fine for most work — Liquibase provisions both databases from the
same changelog. Anything that touches SQL or the schema, though, deserves option A: H2 and
MariaDB do diverge, and the [Testcontainers tests](#testing) exist precisely because of it.

The app comes up on <http://localhost:8080>; `/` redirects to the SPA at `/app/`.
On first start the database is empty — sign in and upload an IMDb CSV export under
**My Watchlist** to populate your list.

## Configuration

Deployment secrets go in `.env` (copy [`.env.example`](.env.example)).
Key properties (`src/main/resources/application.properties`):

| Property | Default | Description |
| --- | --- | --- |
| `server.port` | `8080` | HTTP port — the same locally and in the container |
| `server.servlet.context-path` | *(empty)* | Mount point; the Compose deployment sets `/w2s`. Must match what the reverse proxy forwards — see the comment in `compose.yml` |
| `server.forward-headers-strategy` | `native` | Read the real scheme/host from `X-Forwarded-*` behind the TLS-terminating proxy. **Fails quietly** when the peer is outside Tomcat's trusted ranges: redirects silently go out as `http` again |
| `server.servlet.session.timeout` | `30m` | Idle timeout for a signed-in session |
| `wer-streamt.invalidate.after-days` | `28` | Days before a cached lookup is considered stale |
| `wer-streamt.invalidate.jitter-min-factor` / `-max-factor` | `1.5` / `2.0` | Staggering window (as a multiple of `after-days`) for the background refresh due date, so titles cached together don't all become due at once ([ADR-0016](docs/adr/0016-asynchronous-deferred-cache-refresh.md)) |
| `wer-streamt.rate-limit.requests-per-second` | `20` | Outbound throttle for werstreamt.es (`<= 0` disables) |
| `wer-streamt.background-refresh.enabled` | `true` | Off switch for the proactive scheduled cache-refresh job |
| `wer-streamt.background-refresh.cron` | `0 0 4 * * *` | When that job runs |
| `imdb-poster.rate-limit.requests-per-second` | `10` | Outbound throttle for the IMDb poster lookup (`<= 0` disables) |
| `poster.negative-cache-days` | `14` | How long a "no poster" result is cached before re-checking |
| `tmdb.enabled` | `false` | Use TMDB instead of IMDb as the poster source; also needs `tmdb.api-key` |
| `tmdb.api-key` | *(blank)* | TMDB v3 API key (required when `tmdb.enabled=true`) |
| `spring.jpa.hibernate.ddl-auto` | `none` | The schema belongs to Liquibase alone |

---

# Using it

**Sign in.** The app requires a login; there is no anonymous view. On an empty user table an
`admin` account is seeded — set its password via `w2s.security.initial-admin.password`, otherwise
a strong one is generated and logged once at startup.

**Import your watchlist.** *My Watchlist* → upload your IMDb CSV export. The import is a **full
sync of your list**: new titles are added, changed ones updated, and titles missing from the
upload are removed. Malformed rows are skipped and logged rather than failing the whole import.

**Browse.** The dashboard shows everything with the services it is available on; the provider
pages split one service into what is included in the subscription and what costs extra. Both
views come as a sortable table or a poster grid, switchable in the navbar.

A title you have just added is resolved on first view, which takes a moment. After that it comes
from the cache. A title whose cache entry has gone stale is still shown **immediately**, with a
banner saying so, while the refresh runs in the background — only a title with nothing cached at
all makes you wait ([ADR-0016](docs/adr/0016-asynchronous-deferred-cache-refresh.md)).

**Adjust it.** *Settings* holds language, theme, German titles, age-rating badges, grid density
and the eBay marketplace your search links open.

**As an admin.** *Manage cache* lists every title with the time it was last scraped, and lets you
invalidate or re-scrape a selection. *Users* manages accounts and passwords, and starts an
impersonation when you need to see what someone else sees. *Metrics* shows how much this instance
holds and how well its caches are filled — including how much of each cache is a "there is nothing
here" entry rather than real data, which a plain row count would hide.

---

# Technical

## Stack

Java 25 · Spring Boot 4 (MVC, JSON API) · Spring Security · Spring Data JPA · Liquibase ·
MapStruct · Lombok · jsoup · Apache Commons CSV · Maven —
and an Angular 22 SPA (standalone, zoneless, signals) with Angular Material M3.

The SPA under `/app` is the only UI. The one server-rendered page left is the login page.

## How a title gets resolved

1. `ExportReader` parses the uploaded CSV into `ImdbEntry` records; `WatchlistImportService`
   persists them per user id.
2. `WerStreamtEsSource` scrapes werstreamt.es per title. Results are cached in the database
   (`StreamInfoService`), keyed by IMDb id and **shared across users**; outbound requests are
   rate-limited to stay polite.
3. Stale entries are served from cache and refreshed in the background; a scheduled job
   proactively refreshes titles nobody is currently looking at.
4. The SPA renders the aggregate per streaming service.

## Architecture

The backend is organised **by bounded context first, ports & adapters second**:
`accountaccess`, `watchlist`, `titlecatalog` and `streamingavailability`, each with its own
`domain` → `application` → `port` → `adapter` tree, plus a deliberately minimal `shared`.
A context reaches another only through a published port — enforced by `ArchitectureTest` (ArchUnit).

The full picture is in [`CLAUDE.md`](CLAUDE.md); the reasoning behind each decision is in
[`docs/adr/`](docs/adr/README.md). Both are kept current, which is why this section is short.

## Database & schema

The database holds user accounts, per-user watchlists, persistent HTTP sessions and the global
cached scrape results. The schema is created and versioned by **Liquibase** as portable XML
changelogs (`src/main/resources/db/changelog/`), so one changelog provisions both MariaDB and H2.
Hibernate neither creates nor validates it (`ddl-auto=none`).

**Production runs on MariaDB.** H2 is the test database and the zero-setup option for a local
run (see [Locally](#locally)).

> **Do not delete an existing `./db` to get a clean baseline.** It once held nothing but scrape
> results — today it also holds user accounts, watchlists, sessions and cached title metadata.

## Authentication & users

Users live in the database with `USER` / `ADMIN` roles: reading needs any authenticated user,
while maintenance endpoints and user administration need `ADMIN`. Rationale in
[ADR-0006](docs/adr/0006-authentication-and-authorisation.md).

- **Login:** form login and HTTP Basic (`curl -u admin:… http://localhost:8080/api/status`).
- **Staying signed in:** sessions live in the database (Spring Session JDBC), so a restart does
  not log everyone out. Tick *Stay signed in* for a login that survives closing the browser, and
  set a stable `w2s.security.remember-me.key` so those tokens survive restarts too.
- **Google login (optional):** start with `SPRING_PROFILES_ACTIVE=google` plus
  `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` (redirect URI `{baseUrl}/login/oauth2/code/google`).
  The first OIDC login provisions a local `USER` keyed by e-mail.

## Poster images

The image **source** is pluggable (`PosterSource`). A title's poster reference is resolved once,
both sizes are downloaded **pre-sized from the source's CDN** (no server-side image processing)
and cached as BLOBs per `imdbId`, so the source is hit at most once per title. The browser then
caches each image (long, immutable `Cache-Control` + `ETag`).

- **IMDb (default, no key):** the URL comes from IMDb's public GraphQL API; the Amazon image CDN
  resizes on the fly via URL parameters. HTML scraping of the title page does *not* work
  server-side — `www.imdb.com` answers datacenter IPs with an empty `202`.
  Note that this returns IMDb data under their terms (limited non-commercial use); TMDB below is
  the unambiguous path.
- **TMDB (opt-in):** `TMDB_ENABLED=true` plus a free v3 key. When TMDB is the active source the UI
  shows the required attribution notice; with IMDb it shows none.
- A title with no poster is negatively cached (`poster.negative-cache-days`).

## Endpoints

The SPA lives at `/app/` (hash-routed), the JSON API under `/api`, and `/public/status` is an
unauthenticated health probe. `/` redirects to the SPA.

There is deliberately **no endpoint list here**. The SPA is the only consumer of this API and
lives in the same repository, so a second copy of the paths would only be one more thing to keep
in step — and it did not stay in step. The authoritative list is the `@RestController`s under
each context's `adapter/in/api`.

## Testing

```bash
mvn verify                          # backend, incl. the MariaDB Testcontainers tests
mvn verify -Pno-testcontainers      # without a container runtime
cd src/main/frontend && npm test    # frontend (vitest), watch mode
```

The MariaDB tests run **by default** — they are the only ones that exercise the Liquibase
changelog against the database production actually uses, and a check you have to remember is a
check that gets skipped. They are excluded only where no Docker socket exists, notably inside the
image build stages.

Coverage: JaCoCo for the backend (`target/site/jacoco/`), Vitest v8 for the frontend
(`npm run test:coverage`).

Two rules are enforced rather than agreed: bounded-context isolation and "no `Instant.now()` /
`Date.now()` outside the `TimeService` facade" ([ADR-0003](docs/adr/0003-time-through-a-timeservice-facade.md))
— by ArchUnit in `mvn verify` and by ESLint in `npm run lint`.
`DocumentationConsistencyTest` additionally checks that the open TODOs and the ADR index still
point at things that exist.

## Frontend development

For a fast edit/reload loop, run backend and dev server separately:

```bash
mvn spring-boot:run -Dskip.frontend=true            # backend on :8080
cd src/main/frontend && npm start                   # ng serve on :4200, proxies /api -> :8080
```

The Angular app follows a smart/dumb split: containers under `features/` own all data loading,
presentational components under `shared/` only render their inputs.

## Operations

`update-and-restart.sh` (pull, rebuild, restart) and `upgrade-spring-boot.sh` (bump the Spring
Boot parent, test in the image, push) run on the host, driven by `cron.sh`.

---

## Contributing and project status

Conventions, decisions and open work are signposted in [`CONTRIBUTING.md`](CONTRIBUTING.md).
Open items are in [`TODOs.md`](TODOs.md), finished ones in [`DONE.md`](DONE.md).
