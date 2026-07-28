# w2s (where-to-stream)

Spring Boot 4 / Java 25 backend + Angular 22 SPA frontend. Layered backend:
`api/` → `application/` → `services/` → `persistence/`, over a `domain/` leaf
(`configurations/`, `time/` cross-cutting), enforced by `ArchitectureTest`.

## Before writing or reviewing code

- **Check `docs/adr/README.md`** for existing architecture decisions before making a
  design, stack, or convention call the project may have already settled (time handling,
  Optionals, domain value objects, OSIV, test libraries, …). Don't re-litigate a decision
  that already has an ADR — extend it if it turns out to be wrong, don't just diverge.
- **Check `.claude/skills/`** for a skill matching the task before writing it a different
  way. In particular: **when writing or reviewing a test that checks several fields of the
  same object with multiple sequential `assertThat(...)` calls, load and apply the
  `consolidate-test-assertions` skill** (`.claude/skills/consolidate-test-assertions/SKILL.md`)
  — collapse them into one `extracting(...).isEqualTo(...)` / `containsExactly(...)` assertion
  per ADR-0005, instead of a run of single-value checks that hides everything after the first
  failure. This applies whether the test is brand new or already exists and is being touched
  for another reason.
- Neither of these is automatically enforced (no lint rule or pre-commit hook greps for the
  anti-patterns yet) — actively check both before considering test/review work done, don't
  wait for a reminder.

## Testing

- Backend: AssertJ + Mockito + JUnit 5 only (ADR-0005). No Hamcrest, no JUnit `Assertions.*`
  in test bodies — both are structurally still on the classpath (Testcontainers needs
  `junit:junit`'s `TestRule` interface at class-load time; Spring's `jsonPath(...).value(...)`
  needs `org.hamcrest.Matcher` resolvable at compile time for its overload set) but neither is
  meant to be used directly; see ADR-0005 for why removing them outright breaks the build.
- Frontend: Vitest (ADR-0004).
