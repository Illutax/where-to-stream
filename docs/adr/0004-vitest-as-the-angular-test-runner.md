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
  selectors (e.g. `MatCheckboxHarness` in `manage-table.spec.ts`), loaded via
  `TestbedHarnessEnvironment` from `@angular/cdk/testing/testbed`.
- HTTP is tested with `provideHttpClientTesting` / `HttpTestingController`; smart containers
  are driven through the outputs of their child components (`By.directive(...)`).
- The frontend tests are **decoupled from the Maven build** (they run via npm), so that
  `mvn package` stays lean (see also [ADR-0002](0002-frontend-build-via-exec-maven-plugin.md)).

## Consequences

**Easier / better:**

- The Angular 22 standard: fast ESM-native execution, works with a zoneless TestBed
  without extra setup; CDK harnesses (checkbox/button/table) work with jsdom.
- No browser/Karma setup needed; `test:ci` runs headless in a single pass.

**Harder / drawbacks:**

- jsdom is not a real browser: layout and CDK overlays (e.g. the `mat-select` overlay,
  the snackbar container) are limited.
  Countermeasures: a native `<select matNativeControl>` instead of `mat-select`, harnesses instead
  of DOM internals, no assertions that need real layout.
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
