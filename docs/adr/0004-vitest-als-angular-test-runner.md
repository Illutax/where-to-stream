# 0004. Vitest als Test-Runner für den Angular-Client

- **Date**: 2026-07-19
- **Status**: Accepted

## Context

Der Angular-22-Client (`src/main/frontend`) braucht Unit-/Component-Tests. Der bisherige
Angular-Default Karma+Jasmine ist deprecated und in Angular 22 nicht mehr das Gerüst der Wahl.
Angular 22 bringt den Builder `@angular/build:unit-test`, der standardmäßig **Vitest** (mit
jsdom-Umgebung) verwendet; das CLI-Scaffolding legt entsprechend `vitest` als devDependency an.

Die App ist standalone, **zoneless** und Signal-basiert; die UI nutzt Angular Material (CDK).

## Decision

Tests laufen über **Vitest** via `@angular/build:unit-test` (jsdom), so wie vom Angular-22-CLI
vorgegeben. Konkrete Festlegungen:

- Test-Ziel in `angular.json`: `@angular/build:unit-test`; `tsconfig.spec.json` mit
  `types: ["vitest/globals"]`.
- Skripte: `npm test` (Watch), `npm run test:ci` (Single-Run, `ng test --watch=false`).
- Für Material-DOM werden **Angular CDK Component Harnesses** statt fragiler CSS-Selektoren
  genutzt (z. B. `MatCheckboxHarness` in `manage-table.spec.ts`), geladen über
  `TestbedHarnessEnvironment` aus `@angular/cdk/testing/testbed`.
- HTTP wird mit `provideHttpClientTesting` / `HttpTestingController` getestet; Smart-Container
  werden über die Outputs ihrer Kind-Komponenten (`By.directive(...)`) getrieben.
- Die Frontend-Tests sind **vom Maven-Build entkoppelt** (laufen über npm), damit `mvn package`
  schlank bleibt (siehe auch [ADR-0002](0002-frontend-build-via-exec-maven-plugin.md)).

## Consequences

**Einfacher / besser:**

- Der Angular-22-Standard: schnelle ESM-native Ausführung, funktioniert mit zoneless TestBed
  ohne Zusatz-Setup; CDK-Harnesses (Checkbox/Button/Table) funktionieren mit jsdom.
- Kein Browser/Karma-Setup nötig; `test:ci` läuft headless in einem Durchlauf.

**Schwieriger / Nachteile:**

- jsdom ist kein echter Browser: Layout und CDK-Overlays (z. B. `mat-select`-Overlay,
  Snackbar-Container) sind eingeschränkt. Gegenmaßnahmen: natives `<select matNativeControl>`
  statt `mat-select`, Harnesses statt DOM-Interna, keine Assertions, die echtes Layout brauchen.
- Zwei getrennte Test-Toolchains (Vitest vorne, JUnit hinten) — bewusst in Kauf genommen, da
  Frontend und Backend getrennte Build-Schritte sind.

## Alternatives Considered

- **Karma + Jasmine**: bisheriger Angular-Default, aber deprecated und in Angular 22 nicht mehr
  vorgesehen. Verworfen.
- **Jest**: verbreitet, aber für Angular ein Extra-Setup (`jest-preset-angular`, ESM-/Transform-
  Konfiguration) und nicht mehr der Angular-Default. Mehr Reibung ohne Mehrwert gegenüber Vitest.
- **Web Test Runner / Playwright Component Testing**: echter Browser, höhere Treue, aber deutlich
  schwerer und langsamer als für diese interne App nötig. Bei Bedarf an echten Browser-/E2E-Tests
  später ergänzbar, nicht als Ersatz für die Unit-Ebene.
