---
name: run-tests
description: Runs this project's checks — backend and frontend, in the right combination for the situation. Use whenever you need to know whether the code is green, after a change, or before a commit.
---

# Running the checks

## While working — ~60 s, everything

```bash
cd /workspace/w2s && mvn test -Dskip.frontend=true          # 47 s, 426 tests
cd /workspace/w2s/src/main/frontend && npx ng lint && npx ng test --watch=false   # 13 s, 249 tests
```

`-Dskip.frontend=true` keeps Maven from building the Angular client, which the backend tests do not
need.
Testcontainers stay **in**: they cost 11 s now, and a check that has to be remembered is a check
that gets skipped.

## Before committing

```bash
cd /workspace/w2s && mvn verify     # 59 s
```

Same tests plus the production Angular build and the packaged jar.

Only leave the containers out where there is no container runtime at all
(`-Pno-testcontainers`, 36 s, 408 tests).
It is an escape hatch for an environment that cannot run them, not a faster mode.

## Where the time goes

Measured on this machine (8 cores) on 2026-09-09, warm caches,
before and after the four MariaDB tests were put on one shared container:

| | Before | After | Tests |
| --- | --- | --- | --- |
| `mvn verify` | 94 s | **59 s** | 426 |
| `mvn test -Dskip.frontend=true` | 91 / 102 / 114 s | **47 s** | 426 |
| the same, `-Pno-testcontainers` | 36 s | 36 s | 408 |
| the 18 container tests alone | 69 s | **30 s** | 18 |
| `ng lint` + `ng test` | 13 s | 13 s | 249 |

Three results, all of which contradicted the obvious guess:

- **The container was the whole cost, and it was self-inflicted.**
  Four `*MariaDbTest` classes each started their own MariaDB — in two different images
  (`mariadb:11` and `mariadb:lts-ubi`), so the changelog was being verified against two server
  versions, neither deliberately chosen.
  One shared static container (`SharedMariaDb`) makes the four merged context configurations
  identical, so Spring's context cache serves all four from one context: one boot, one migration,
  one EntityManagerFactory.
  58 s of overhead became 11 s.
- **`mvn test` instead of `mvn verify` saves nothing.**
  The difference is smaller than the spread between two identical `test` runs (91 / 102 / 114 s for
  the same command).
  Packaging is cheap; the tests are not.
- **Running the two halves in parallel is not worth the loss of legibility.**
  Sequential is 47 + 13 = 60 s, backgrounding both is 57 s — 3 s, because they contend for the same
  cores.
  It was 5 s before the container work too, so this was never the lever it looked like.
  On a machine with more idle cores the case would be better; here it is not.

Also measured and rejected: `-DforkCount=2` (98 s) and `-DforkCount=4` (112 s) against 114 s for
one fork, back when there were four containers.
Parallel JVMs did not help, because the cost was container boots, not CPU.

## Things to know before changing a test

- **The MariaDB container is shared** (`src/test/java/.../testing/SharedMariaDb.java`).
  This is safe only because all four `*MariaDbTest` classes are `@DataJpaTest` and therefore roll
  back.
  A test that commits — `@Commit`, `propagation = NOT_SUPPORTED`, its own thread — leaks rows into
  the other three and has to clean up after itself.
- Adding a *fifth* annotation or a `@TestPropertySource` to only one of them splits the context
  cache and quietly buys back a second container.
  If the container count rises, that is why: `grep -c "Creating container for image"` in the build
  log should be `1`.

## Rules that cost time to learn

- **Run Maven from the repository root.**
  Started in `src/main/frontend` it fails with "no POM in this directory", which reads like a build
  failure and is not one.
  This happened three times in one session, always after an earlier `cd` in the same command.
- **Assert on the summary line. Never read silence as success.**
  A `grep` filter that does not match the failure output makes a broken run look like a clean one —
  once reported here as "four tests green" when the test had not compiled at all.
  Check `Tests run: …` and `BUILD`, and treat missing output as "I do not know", not as "fine".
- **Testcontainers are a Maven profile, not a JUnit group.**
  `-Dgroups=testcontainers` silently runs zero tests.
  It is `-Pno-testcontainers` to leave them out; they are on by default.
- **A stale class file can fake a run.**
  After deleting or renaming a test, clear the matching file under `target/test-classes/` —
  otherwise Surefire may still find the old class.
- Surefire only picks up classes matching `*Test`, `Test*`, `*Tests`, `*TestCase`.
  A throwaway named `SomethingProbe` compiles and never runs.
- In zsh, quote `-Dtest` patterns: `'-Dtest=*MariaDbTest'`.
  Unquoted, the shell tries to glob it and aborts before Maven starts.
