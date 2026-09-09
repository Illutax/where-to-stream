---
name: run-tests
description: Runs this project's checks — backend and frontend, in the right combination for the situation. Use whenever you need to know whether the code is green, after a change, or before a commit.
---

# Running the checks

Two modes.
Pick by what you are about to do, not by habit.

## While working — the inner loop, ~44 s

Backend without Testcontainers, frontend alongside it, both at once:

```bash
cd /workspace/w2s
( mvn test -Dskip.frontend=true -Pno-testcontainers > /tmp/be.log 2>&1; echo $? > /tmp/be.rc ) &
( cd src/main/frontend && npx ng lint > /tmp/fe.log 2>&1 && npx ng test --watch=false >> /tmp/fe.log 2>&1; echo $? > /tmp/fe.rc ) &
wait
grep -E "Tests run: .*Skipped: [0-9]+$|BUILD" /tmp/be.log | tail -2
grep -E "Test Files|Tests " /tmp/fe.log | tail -2
cat /tmp/be.rc /tmp/fe.rc
```

`-Dskip.frontend=true` is what makes the two halves safe to run together.
Without it Maven builds the Angular client itself, and both would be writing into `dist/`.

## Before committing — the full round, ~95 s

```bash
cd /workspace/w2s && mvn verify
```

This is the only run that includes the 18 MariaDB Testcontainers tests,
and those are the only tests that exercise the Liquibase changelog against the database production
actually uses.
Do not commit a schema change without it.

## Where the time actually goes

Measured on this machine on 2026-09-09, warm caches:

| | Wall clock | Tests |
| --- | --- | --- |
| `mvn verify` | 94 s | 426 |
| `mvn test -Dskip.frontend=true` | 91 / 102 / 114 s | 426 |
| the same, `-Pno-testcontainers` | **36 s** | 408 |
| `ng lint` + `ng test` | 13 s | 249 |
| backend (no containers) ∥ frontend | **44 s** | 408 + 249 |

Two results worth keeping, because both contradict the obvious guess:

- **`test` instead of `verify` saves nothing.**
  The gap between them is smaller than the spread between two identical `test` runs.
  Packaging the jar is cheap; the tests are not.
- **Parallelism is not the lever either.**
  The frontend is 13 s of a 95 s round — worth overlapping because it is free, but it is not where
  the time is.
  The container is: **18 tests cost about 60 s.**

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
  It is `-Ptestcontainers` to force them on, `-Pno-testcontainers` to leave them out; on by default.
- **A stale class file can fake a run.**
  After deleting or renaming a test, clear the matching file under `target/test-classes/` —
  otherwise Surefire may still find the old class.
- Surefire only picks up classes matching `*Test`, `Test*`, `*Tests`, `*TestCase`.
  A throwaway named `SomethingProbe` compiles and never runs.
