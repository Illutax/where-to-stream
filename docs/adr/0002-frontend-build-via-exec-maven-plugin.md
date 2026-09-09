# 0002. Frontend build via exec-maven-plugin (system Node) instead of frontend-maven-plugin

- **Date**: 2026-07-19
- **Status**: Accepted

## Context

The Angular client (`src/main/frontend`) has to remain part of the **single** fat JAR: `mvn package`
builds the frontend and copies `dist/w2s-ui/browser` to `static/app` on the classpath.
For that, the Maven build has to invoke a Node/npm toolchain during `package` (`npm ci` +
`npm run build`).

Constraints of the build environment:

- Dev container: Node v24.18.0, npm 11.16.0 present; `registry.npmjs.org` reachable via a
  Nexus mirror.
  `mvn deploy` is blocked, `mvn package` works.
- The Docker builder stage is based on `maven:3-amazoncorretto-25-alpine` (musl/Alpine).
- Whether `nodejs.org` is reachable (for a Node self-download) is **not verified** in the
  sandbox/proxy setup.
- `node_modules` is ~300 MB; `npm ci` deletes and reinstalls it on every invocation.

Two established ways to call npm from Maven: `exec-maven-plugin` (calls a **pre-installed**
system npm) or `com.github.eirslett:frontend-maven-plugin` (downloads a Node version **pinned** in
`pom.xml` from `nodejs.org` itself).

## Decision

The frontend build runs through **`exec-maven-plugin`** with the system `npm`, bound to the phase
`generate-resources` (`npm run build`), plus a separate, guarded `npm ci` execution.
`-Dskip.frontend=true` skips the entire frontend build (backend only).

On top of that, three hardening measures are in place (see also Consequences):

1. **Node version pinning without a plugin**: `engines` (`node: ">=22 <25"`) in `package.json`,
   `engine-strict=true` in `src/main/frontend/.npmrc`, `.nvmrc` for dev laptops; the
   Docker builder copies a fixed Node out of `node:24-alpine` instead of an unpinned
   `apk add nodejs`.
2. **`npm ci` only when the lock file changed**: a `maven-antrun-plugin` `uptodate` check
   compares `node_modules/.package-lock.json` (written by npm after every installation) against
   `package-lock.json`; an Ant `unless:set` guard runs `npm ci` only when the deps are stale.
   With an unchanged lock file, this saves the ~300 MB reinstallation.
3. **Windows compatibility**: an OS-activated Maven profile `windows` sets
   `npm.executable=npm.cmd`; the build references `${npm.executable}`.

## Consequences

**Easier / better:**

- **Demonstrably works in all three environments** (dev container, Docker builder, local
  builds) without depending on `nodejs.org` — whose reachability is unverified here.
- Simple and transparent: an `exec` call to `npm`; `exec-maven-plugin` is a standard plugin
  with trivial maintenance that knows nothing about Node itself (no plugin updates for new Node
  majors).
- Uses the existing npm setup including its registry configuration — a fit for the project's
  Nexus mirror policy.
- Thanks to hardening 1, the Node version is controlled across machines; a wrong Node aborts the
  install with a clear message thanks to `engine-strict`, instead of causing subtle failures.
- Thanks to hardening 2, a repeated `mvn package` with an unchanged lock file no longer costs a
  300 MB reinstallation.
- `-Dskip.frontend=true` offers a clean backend-only path.

**Harder / drawbacks:**

- **"Node must be pre-installed"** remains an implicit prerequisite: a fresh machine without any
  Node at all fails with `Cannot run program "npm"` (not with a meaningful message).
- The Docker builder pins Node through an additional multi-stage copy (`node:24-alpine`) —
  marginally more Dockerfile complexity than an `apk add`.
- Reproducibility still partly rests on discipline (pinning via `engines`/`.nvmrc`), not on a
  tool that enforces and ships the toolchain.
- The `npm ci` guard logic (antrun `uptodate` + `unless:set`) is build-specific logic one has to
  know about; its correctness was verified in both directions (skips with an up-to-date lock file,
  runs with a touched lock file).

## Alternatives Considered

**`com.github.eirslett:frontend-maven-plugin`** (downloads a pinned Node version itself).

Pro: a pinned Node/npm version directly in `pom.xml` → an identical toolchain on CI, the Docker
builder and every dev laptop, via `mvn package` without any pre-installation; Windows out of the
box; air-gap capable, **as soon as** a Nexus raw proxy for `nodejs.org/dist` exists.

Contra (decisive against this option):

- **Self-download from `nodejs.org` is the default — and it is precisely that reachability which is unverified in the sandbox/proxy setup.**
  Without a working download, every build fails hard.
  The remedy (`nodeDownloadRoot` pointing at a Nexus raw repo) requires a **new Nexus repo
  configuration that does not exist today** (raw format).
- In the Docker builder, a duplicate Node installation, or a download inside the `mvn package`
  layer that cannot be layer-cached.
- The plugin is more or less in maintenance mode (slow release cycles; new Node majors
  occasionally require plugin updates).
- It does **not** solve the `npm ci`-on-every-build problem — it runs the same npm goals.

Assessment: the only substantial advantage (a pinned Node version) hangs, in this environment, on
an unverified prerequisite; switching would trade a theoretical reproducibility problem for a real
build-breaking risk.
The gaps in the chosen solution
can be closed more cheaply (the three hardening measures above), so `exec-maven-plugin` stays.

**Decide differently if:** the Nexus instance gets a raw proxy repo for `nodejs.org/dist`
(then the trade-off flips: a pinned Node version + full Nexus coverage with zero
internet dependency), the team grows and heterogeneous dev machines (especially Windows without
pre-installed Node) come along, a concrete bug from Node version drift shows up, or
CI runners without pre-installed Node are introduced.
In those cases `frontend-maven-plugin` with `nodeDownloadRoot` pointing at Nexus is the right choice —
the migration is then a manageable pom rework with no change to the frontend itself.
