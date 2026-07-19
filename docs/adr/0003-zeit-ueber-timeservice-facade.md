# 0003. Zeit über eine TimeService-Facade statt statischer now()-Aufrufe

- **Date**: 2026-07-19
- **Status**: Accepted

## Context

Produktionscode las die Uhrzeit direkt über statische Aufrufe: im Backend `Instant.now()` /
`LocalDate.now()` (u. a. in `StreamInfoService` für die Cache-Frische und in `StatusService`
für die Server-Startzeit), im Angular-Client stünden `Date.now()` / `new Date()` zur Verfügung.

Statische Zeitaufrufe machen Tests von der Wall-Clock abhängig: Zeitpunkte lassen sich nicht
exakt zusichern, und zeitabhängige Logik (Cache-Ablauf nach 28 Tagen, Server-Startzeit) muss mit
relativen Offsets (`Instant.now().minus(40, DAYS)`) statt mit konkreten Werten getestet werden —
weniger ausdrucksstark und potenziell flaky.

## Decision

Zeit wird ausschließlich über eine **TimeService-Facade** gelesen; direkte statische
`now()`-Aufrufe sind in Produktionscode nicht mehr erlaubt.

- **Backend**: Interface `tech.dobler.werstreamt.time.TimeService` (`Instant now()`,
  `LocalDate today()`) mit Produktions-Implementierung `SystemTimeService` (`@Service`, ruft die
  echten statischen Methoden). `StreamInfoService` und `StatusService` bekommen den `TimeService`
  injiziert. Das Facade liegt in einem eigenen, abhängigkeitsfreien Paket `time`, sodass sowohl
  `services` als auch `application` es ohne neue Paketzyklen nutzen können.
- **Frontend**: `@Injectable({ providedIn: 'root' })` `TimeService` (`now(): number`,
  `nowDate(): Date`), das `Date.now()` / `new Date()` kapselt. Der Client liest aktuell keine
  Zeit direkt (Server-Zeitstempel werden unverändert angezeigt) — der Service ist der
  vorgeschriebene Einstiegspunkt für künftige Fälle.

In Tests wird eine feste Uhr eingesetzt: im Backend per Mockito
(`when(timeService.now()).thenReturn(NOW)`), im Frontend per DI-Override
(`{ provide: TimeService, useValue: … }`).

## Consequences

**Einfacher / besser:**

- Zeitabhängige Tests sind deterministisch und ausdrucksstark: `StreamInfoServiceTest` prüft
  Cache-Frische gegen eine feste `NOW`, `StatusServiceTest` zeigt exakt, dass die Startzeit einmal
  bei Konstruktion gelesen wird (`verify(timeService, times(1)).now()`).
- Einheitliches Muster über Backend und Frontend; die Regel „keine statischen now()-Aufrufe"
  **wird automatisch erzwungen**: im Backend per **ArchUnit** (`ArchitectureTest`, Ausnahme nur
  `SystemTimeService`), im Frontend per **ESLint** (`no-restricted-syntax` gegen `Date.now()` /
  `new Date()`, Ausnahme nur `core/time-service.ts` und Specs).

**Schwieriger / Nachteile:**

- Eine zusätzliche Indirektion und ein injizierter Kollaborator mehr.
- `Instant.now(Clock)` / `new Date(arg)` bleiben erlaubt (deterministisch bzw. Parsen) — die
  Regeln zielen bewusst nur auf die argumentlosen „jetzt"-Aufrufe.

## Alternatives Considered

- **`java.time.Clock` (Backend-Idiom).** Spring/JDK-üblich; Tests mit `Clock.fixed(...)`. Verworfen
  zugunsten einer über Backend und Frontend **einheitlichen** Facade mit schmaler API
  (`now()`/`today()`). `SystemTimeService` könnte intern später problemlos einen `Clock` nutzen.
- **Nur Test-seitige Fake-Timer** (Backend: keiner nötig; Frontend: `vi.useFakeTimers()`).
  Verworfen als alleinige Lösung, weil das Produktions-`Date.now()` dann verstreut bliebe und die
  DI-Substitution expliziter und konsistent mit dem Backend ist. Fake-Timer bleiben zusätzlich
  nutzbar.
