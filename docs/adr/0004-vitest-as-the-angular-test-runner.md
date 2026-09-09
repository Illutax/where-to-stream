# 0004. Vitest as the test runner for the Angular client

- **Date**: 2026-07-19
- **Status**: Accepted

## Context

The Angular 22 client (`src/main/frontend`) needs unit/component tests.
The previous Angular default, Karma + Jasmine, is deprecated and is no longer the scaffolding of
choice in Angular 22.
Angular 22 ships the builder `@angular/build:unit-test`, which by default uses **Vitest** (with a
jsdom environment); the CLI scaffolding accordingly adds `vitest` as a devDependency.

The app is standalone, **zoneless** and signal-based; the UI uses Angular Material (CDK).

## Decision

Tests run through **Vitest** via `@angular/build:unit-test` (jsdom), just as the Angular 22 CLI
prescribes.
Specific choices:

- Test target in `angular.json`: `@angular/build:unit-test`; `tsconfig.spec.json` with
  `types: ["vitest/globals"]`.
- Scripts: `npm test` (watch), `npm run test:ci` (single run, `ng test --watch=false`).
- For Material DOM, **Angular CDK component harnesses** are used instead of fragile CSS
  selectors (e.g. `MatCheckboxHarness` in `manage-table.spec.ts`, `MatSelectHarness` in
  `settings-page.spec.ts`), loaded via `TestbedHarnessEnvironment` from
  `@angular/cdk/testing/testbed`.
  That includes the overlay-backed harnesses — see the drawbacks below for what was measured.
- HTTP is tested with `provideHttpClientTesting` / `HttpTestingController`; smart containers
  are driven through the outputs of their child components (`By.directive(...)`).
- The frontend tests are **decoupled from the Maven build** (they run via npm), so that
  `mvn package` stays lean (see also [ADR-0002](0002-frontend-build-via-exec-maven-plugin.md)).

## Consequences

**Easier / better:**

- The Angular 22 standard: fast ESM-native execution, works with a zoneless TestBed
  without extra setup; CDK harnesses work with jsdom — checkbox, button-toggle group, sort, and
  the overlay-backed select, which is the full set in use here.
- No browser/Karma setup needed; `test:ci` runs headless in a single pass.

**Harder / drawbacks:**

- jsdom is not a real browser: nothing has a layout, so no assertion may depend on a measured
  size or position.
  Countermeasures: harnesses instead of DOM internals, and no assertion that needs real layout.
- What does *not* follow is that CDK overlays are out of reach.
  This ADR originally named one further countermeasure — a native `<select matNativeControl>`
  instead of `mat-select` — and it has been dropped: `matNativeControl` no longer occurs anywhere
  in the repository, and it is not needed.
  Measured on 2026-09-09 (TODO-69) in `settings-page.spec.ts`: `MatSelectHarness`
  (`@angular/material/select/testing`) drives the `mat-select` overlay in jsdom end to end —
  opening the panel, reading the option labels, and clicking an option so that the resulting
  `selectionChange` reaches the store and its PUT.
  Neither step needs layout: `open()` clicks the trigger, and the panel is found through the
  harness's `documentRootLocatorFactory()`.
  The one restriction that did show up is the harness API, not jsdom: `MatOptionHarness` exposes
  an option's text, never its bound value, so a test that has to pin the value down picks the
  option and asserts on what is then persisted.
  The snackbar overlay is a separate question and remains unmeasured — no spec renders one, they
  provide a stub `MatSnackBar` instead (`seen-store.spec.ts`).
- Two separate test toolchains (Vitest at the front, JUnit at the back) — deliberately accepted,
  since frontend and backend are separate build steps.

## Alternatives Considered

- **Karma + Jasmine**: the previous Angular default, but deprecated and no longer intended for
  Angular 22.
  Rejected.
- **Jest**: widespread, but for Angular it means extra setup (`jest-preset-angular`,
  ESM/transform configuration) and it is no longer the Angular default.
  More friction with no added value over Vitest.
- **Web Test Runner / Playwright component testing**: a real browser, higher fidelity, but
  considerably heavier and slower than this internal app needs.
  Can be added later should real browser/E2E tests be needed, not as a replacement for the unit
  level.
