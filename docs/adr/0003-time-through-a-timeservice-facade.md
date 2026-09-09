# 0003. Time through a TimeService facade instead of static now() calls

- **Date**: 2026-07-19
- **Status**: Accepted

## Context

Production code read the clock directly through static calls: in the backend `Instant.now()` /
`LocalDate.now()` (among others in `StreamInfoService` for cache freshness and in `StatusService`
for the server start time), and in the Angular client `Date.now()` / `new Date()` would have been
available.

Static time calls make tests depend on the wall clock: points in time cannot be asserted exactly,
and time-dependent logic (cache expiry after 28 days, server start time) has to be tested with
relative offsets (`Instant.now().minus(40, DAYS)`) instead of concrete values — less expressive
and potentially flaky.

## Decision

Time is read exclusively through a **TimeService facade**; direct static
`now()` calls are no longer permitted in production code.

- **Backend**: the interface `tech.dobler.where2stream.shared.time.TimeService` (`Instant now()`,
  `LocalDate today()`) with the production implementation `SystemTimeService` (`@Service`, calls
  the real static methods).
  `StreamInfoService` and `StatusService` get the `TimeService` injected.
  The facade lives in the shared `shared` kernel (not in one of the bounded contexts), so that
  every context can use it without becoming dependent on another context (see the ADR
  on the bounded context restructuring).
- **Frontend**: an `@Injectable({ providedIn: 'root' })` `TimeService` (`now(): number`,
  `nowDate(): Date`) that encapsulates `Date.now()` / `new Date()`.
  The client currently reads no time directly (server timestamps are displayed unchanged) —
  the service is the prescribed entry point for future cases.

In tests, a fixed clock is used: in the backend via Mockito
(`when(timeService.now()).thenReturn(NOW)`), in the frontend via a DI override
(`{ provide: TimeService, useValue: … }`).

## Consequences

**Easier / better:**

- Time-dependent tests are deterministic and expressive: `StreamInfoServiceTest` checks
  cache freshness against a fixed `NOW`, `StatusServiceTest` shows exactly that the start time is
  read once at construction (`verify(timeService, times(1)).now()`).
- A uniform pattern across backend and frontend; the rule "no static now() calls"
  **is enforced automatically**: in the backend via **ArchUnit** (`ArchitectureTest`, the only
  exception being `SystemTimeService`), in the frontend via **ESLint**
  (`no-restricted-syntax` against `Date.now()` / `new Date()`, the only exceptions being
  `core/time-service.ts` and specs).

**Harder / drawbacks:**

- One more level of indirection and one more injected collaborator.
- `Instant.now(Clock)` / `new Date(arg)` remain allowed (deterministic and parsing, respectively) —
  the rules deliberately target only the argument-less "now" calls.

## Alternatives Considered

- **`java.time.Clock` (the backend idiom).**
  Common in Spring/the JDK; tests with `Clock.fixed(...)`.
  Rejected in favour of a facade that is **uniform** across backend and frontend, with a narrow API
  (`now()`/`today()`).
  `SystemTimeService` could easily use a `Clock` internally later on.
- **Test-side fake timers only** (backend: none needed; frontend: `vi.useFakeTimers()`).
  Rejected as the sole solution, because production `Date.now()` would then stay scattered, and
  DI substitution is more explicit and consistent with the backend.
  Fake timers remain usable in addition.
