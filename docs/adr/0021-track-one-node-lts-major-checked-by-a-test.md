# 0021. Track one Node LTS major, and let a test hold the places together

- **Date**: 2026-09-09
- **Status**: Accepted

## Context

The permitted Node/npm toolchain was stated in four places, maintained by hand, and they had
already diverged (TODO-54):

| Place | Said |
| --- | --- |
| `src/main/frontend/.nvmrc` | `24` |
| `package.json` → `engines` | `node >=22 <25`, `npm >=10` |
| `package.json` → `packageManager` | `npm@11.16.0` |
| `Dockerfile` → `NODE_BASE_IMAGE` | `node:24-alpine` |

`src/main/frontend/.npmrc` sets `engine-strict=true`, so this is not cosmetic.
A toolchain outside `engines` does not produce a warning — it aborts `npm ci` with `EBADENGINE`,
which was verified by setting the range to `^99.0.0` rather than inferred from the setting's name.
Divergence between these files does not degrade the build, it stops it, and the error names the
offending version rather than the file that disagrees with the others.

Three facts settled the shape of the decision, all of them measured rather than assumed:

1. **Node 24 is the Active LTS** — codename Krypton, LTS from 2025-10-28, maintenance from
   2026-10-20, end of life 2028-04-30. Node 22 is in maintenance; **Node 25 reached end of life on
   2026-06-01**, so the old upper bound `<25` was excluding something already dead. Node 26 became
   Current on 2026-05-05 and becomes LTS on 2026-10-28.
   Taken from the `node-releases` release schedule and then confirmed against
   `nodejs.org/dist/index.json`, which corroborates it independently: 24 is flagged `Krypton`, 26 is
   still `false`, and 25 has had no release since 2026-03-31.
2. **Angular requires `^22.22.3 || ^24.15.0 || >=26.0.0`** — a range with a hole in it, because 25
   was never an LTS. Read from the installed `@angular/core` 22.0.7 and confirmed unchanged in the
   current 22.1.5.
3. **Corepack accepts only an exact version in `packageManager`.** `npm@12`, `npm@^12` and
   `npm@12.x` are each rejected with *"Invalid package manager specification … expected a semver
   version"*; only `npm@12.0.2` resolves. The field therefore cannot express a policy, only a
   snapshot — which is precisely how it came to be stale.

## Decision

**One Node major at a time: the Active LTS.** `engines.node` is a caret range over that major
(`^24.15.0`, the caret floor being Angular's), `.nvmrc` and `NODE_BASE_IMAGE` name the same major.

**`engines.npm` is `>=11`, and deliberately loose.** npm ships with Node; every Node 24 carries at
least npm 11. A tighter pin could only ever contradict the interpreter it comes with.

**`packageManager` is removed.** Nothing in this repository invokes Corepack — the Docker build
symlinks `npm-cli.js` directly — so the field bound nothing while still going out of date in
public view. A declaration that constrains nothing is worse than no declaration: it reads like a
guarantee.

**The remaining places are checked against each other by a test**
(`ToolchainVersionsAgreeTest`), not derived from one another. Deriving would mean generating
`.nvmrc` or templating the Dockerfile, which buys less than it costs; a build that fails the moment
they disagree gets the same result and stays readable.

**There is a fourth place, and it was found by making the mistake.** npm copies `engines` verbatim
into `package-lock.json`, and editing `package.json` alone does not update it — this very change
left the lockfile saying `>=22 <25` for a while, with every other check passing. Worse, the Maven
build guards its `npm ci` with an `uptodate` check against `package-lock.json`, so an edit to
`package.json` skips the install entirely and the mismatch never surfaces. The test compares the
two, and the fix is `npm install --package-lock-only`.

**We do not model Angular's gap.** Writing `^22.22.3 || ^24.15.0 || >=26.0.0` into `engines` would
claim support for three majors we neither build nor test on, and would put the 25-shaped hole back
into a file where nobody can see why it is there. Angular's range is an upstream compatibility
statement; ours is a statement about what we ship.

## Consequences

**Easier:**

- One number to change per Node upgrade, in three files, with a test naming any place that was
  missed.
- The version story is now the same everywhere: local (`nvm` reads `.nvmrc`), install-time
  (`engine-strict`), and image build.
- No exact npm pin to notice, chase, and get wrong again.

**Harder:**

- `engines.node: ^24.15.0` **refuses Node 22 outright**, which the old range allowed. That is
  intended — nothing is tested on 22 — but a contributor on 22 now hits a hard `npm ci` failure
  instead of a silent divergence. The README says which version to install.
- We are committed to moving deliberately. **Node 26 becomes LTS on 2026-10-28**, roughly seven
  weeks from this decision, and nothing here will tell us: the test compares the repository against
  itself, not against the release schedule. Whoever bumps it edits `.nvmrc`, `engines` and
  `NODE_BASE_IMAGE`, runs `npm install --package-lock-only`, and updates this ADR's first table.
- **That blindness is deliberate.** A test that asked `nodejs.org` would turn an upstream release
  or an outage into a red build on a change that touched nothing — and would make the build depend
  on network access it otherwise does not need. Checking the world is a thing to do when upgrading,
  not on every run.
- Removing `packageManager` gives up a reproducible npm version across dev/CI/Docker — in theory.
  In practice it never provided one here, since no Corepack ran.
