# where-to-stream (w2s)

Manage lists of movies to watch and find **where to stream them**.

w2s takes an [IMDb](https://www.imdb.com/) watchlist CSV export, scrapes
[werstreamt.es](https://www.werstreamt.es/) for each title's streaming availability,
caches the results in an embedded H2 database, and presents them as per-provider web pages
(Netflix, Prime Video, Disney+, WOW, Google Play).

## Tech stack

- Java 25, Spring Boot 4.1 (Spring MVC + Thymeleaf)
- Spring Data JPA on H2 (default) or MariaDB, schema managed by **Liquibase** (XML changelogs)
- jsoup (HTML scraping), Apache Commons CSV (IMDb export parsing)
- MapStruct (entity ↔ persistence mapping), Lombok
- Build: Maven

## How it works

1. Export your watchlist from IMDb as CSV and drop it into the `assets/` directory
   (e.g. `assets/2025-01-01_My-List.csv`). On startup the **lexicographically last** file
   in `assets/` is loaded; you can switch lists at runtime under `/list`.
2. `ExportReader` parses the CSV into in-memory `ImdbEntry` records (malformed rows are
   skipped and logged).
3. `WerStreamtEsApiClient` scrapes werstreamt.es per title. Lookups are cached in H2
   (`StreamInfoService`) and considered stale after a configurable number of days. Outbound
   requests are rate-limited to stay polite.
4. Thymeleaf pages render the aggregated availability per streaming service.

## Running locally

Requires JDK 25 and Maven.

```bash
# run the app (defaults to http://localhost:8001)
mvn spring-boot:run

# run the tests
mvn test
```

Put at least one IMDb CSV export in `assets/` before starting, otherwise startup fails
(there is no list to load).

## Running with Docker

The image builds the jar and runs it (see `Dockerfile` / `compose.yml`). `compose.yml`
mounts `./assets` (read-only) and `./logs`, runs on port `8080`, and serves under the context
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

## Configuration

Key properties (`src/main/resources/application.properties`):

| Property | Default | Description |
| --- | --- | --- |
| `server.port` | `8001` | HTTP port (Docker overrides to `8080`) |
| `wer-streamt.path` | `assets` | Directory holding the IMDb CSV export(s) |
| `wer-streamt.invalidate.after-days` | `28` | Days before a cached lookup is refetched |
| `wer-streamt.rate-limit.requests-per-second` | `2` | Outbound throttle for werstreamt.es (`<= 0` disables) |
| `spring.jpa.hibernate.ddl-auto` | `none` | Schema is owned by Liquibase (single source of truth) |

### Database & schema

The database holds only **cached scrape results**. The schema is created and versioned by
**Liquibase** as portable XML changelogs (`src/main/resources/db/changelog/`), so the same
changelog provisions both H2 and MariaDB. Hibernate neither creates nor validates the schema
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

**Pages (Thymeleaf):**

| Path | Description |
| --- | --- |
| `/` | All entries with their available services |
| `/amazon` (`/prime`) | Prime Video: included + paid |
| `/disney`, `/netflix`, `/wow` | Flatrate titles for that service |
| `/google` | Google Play (paid) titles |
| `/list` (GET) / `/list-change` (POST) | View / switch the active list |
| `/public/status` | Version & server start time |

**REST / maintenance:**

| Path | Description |
| --- | --- |
| `/search?imdbId=…` or `?id=…` | Resolve availability for a title |
| `/query?id=…` | Live werstreamt.es query for a title |
| `/pre-cache` | Resolve & cache every entry |
| `/check-pre-cache` | List entries without a cached result |
| `/refresh/all`, `/refresh/seen` | Force-refresh cached results |

> Note: the maintenance endpoints are currently unauthenticated `GET`s with side effects —
> see `TODOs.md` (TODO-5).

## Project status

This is a personal project. Known issues and planned improvements are tracked in
[`TODOs.md`](./TODOs.md).
