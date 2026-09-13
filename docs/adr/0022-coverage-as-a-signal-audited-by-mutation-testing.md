# 0022. Coverage is a signal, not a target — what earns a test, audited by mutation testing

- **Date**: 2026-09-13
- **Status**: Accepted

## Context

Backend line coverage stands at ~96 % (JaCoCo), frontend at ~94 % (Vitest v8), with no gate
enforcing either — measured 2026-09-13.
Two failure modes threaten a suite from opposite sides:

1. **Chasing the number** produces assertion-free tests: they execute lines, claim nothing,
   and break on every refactoring without ever catching a bug.
   A coverage gate (per build or per class) manufactures exactly these, because the cheapest way
   to satisfy it is a test without a meaningful assertion.
2. **Ignoring the number** lets real gaps hide: `WatchlistCatalog` — a published port
   implementation other contexts depend on — sat at 5 of 9 lines untested until 2026-09-13,
   reached only transitively.

Coverage measures **execution, not assertion strength**: a line counts as covered whether or not
any test would notice it changing.
It also carries structural noise — the frontend's ~78 % function coverage is dominated by the
`loadComponent` lazy-import arrows in `app.routes.ts`, which unit tests can never execute and
which are not a defect.

The strongest tests in this suite share one shape: they pin a **guarantee**, not a method —
`DirtyCheckingPersistenceTest` pins ADR-0018, `PublicStatusIsMinimalTest` pins the public
endpoint's shape, `ToolchainVersionsAgreeTest` pins ADR-0021, `ArchitectureTest` pins ADR-0014.

## Decision

**1. No coverage gate in the build.**
The reports stay (`target/site/jacoco/`, `npm run test:coverage`); the numbers are *reviewed* at
architecture-review cadence and outliers explained, never enforced per build or per class.
Coverage is used the way the 2026-09-13 look at it was: as a pointer to untested behaviour,
of which only the explanation-resistant part gets closed.

**2. What earns a test, in priority order:**

1. **Published contracts** — port implementations, ADR promises, API shapes.
2. **Failure paths** — what happens when the dependency is down, the input malformed,
   the queue full (the 2026-09-10 review found the failure side weakest; TODO-74's tests are
   worth more than ten happy paths).
3. **Mappings and edge cases** — e.g. the null-URL entry in `WatchlistCatalogTest`.
4. **Regressions** — every fixed bug gets the test that would have caught it.
5. Plain delegation — only when rule 1 applies (it is a published contract), otherwise not at all.

**3. The review question for every new test:**
*"Which change should turn this red — and would that change be a bug?"*
If the honest answer is "any refactoring of the internals", the test pins structure instead of
behaviour and is rejected — that includes mock-echo tests that only `verify` one of our own
classes called another.
Tests may also be **deleted** when a higher-level test provably covers the same guarantee.
Test at the public seam (port/service, or the deliberately package-private parse seams),
not at private helpers.

**4. Mutation testing audits assertion strength, at architecture-review cadence.**
Coverage says code ran; only a killed mutant says an assertion bites.
The backend runs [pitest](https://pitest.org) (configured in `pom.xml`, deliberately bound to no
lifecycle phase):

```bash
mvn test-compile org.pitest:pitest-maven:mutationCoverage    # report: target/pit-reports/
```

It runs as part of every architecture review (anchored in
`.claude/skills/architecture-review/SKILL.md`), not per build — the run costs minutes, and a
mutation score behind a gate is exactly as gameable as a coverage number.
Surviving mutants in code the review leans on become findings and tickets like any other;
a surviving mutant in trivia is allowed to survive.
The MariaDB Testcontainers tests are excluded from mutation runs (they guard the schema, not
mutable logic, and a container boot per mutant would dominate the cost); the same non-logic
classes JaCoCo excludes (MapStruct impls, the boot class) are excluded here.
Incremental analysis (`withHistory`) is unavailable — since pitest 1.30 it requires the
commercial arcmutate history plugin — so every run is a full run.

**5. The frontend half is decided in principle and deferred in practice.**
`@angular/build:unit-test` owns the Vitest configuration internally, and Stryker's Vitest runner
needs a standalone config replicating the Angular compile pipeline — an integration to evaluate,
not a flag to flip.
Tracked as TODO-84; until then the frontend relies on rules 1–3 plus review.

## Consequences

**Easier:**

- Coverage keeps its diagnostic value precisely because nobody is paid in it.
- "Too many / wrong tests" has a named rejection criterion (rule 3) instead of taste.
- Assertion strength is measured, not assumed — full-backend baseline (2026-09-13, 12 min on
  8 cores): 766 mutants, 637 killed (83 % mutation score, 87 % test strength, 30 mutants in
  uncovered code). The gap between 96 % line coverage and 83 % killed is exactly the blind spot
  coverage alone cannot see.

**Harder / accepted:**

- A review-cadence check can be forgotten; the anchor in the `architecture-review` skill is the
  mitigation, and a skipped mutation run must be listed in that review's "not examined" header.
- Full mutation runs cost minutes and re-examine everything each time (no free incremental mode).
- Rules 2–3 are judgement, not machinery: nothing greps for a mock-echo test.
  Enforcement stays with review — the same trade ADR-0005/0010/0018 already accept.
