# 0008. Thymeleaf-Client entfernen — SPA-only UI

- **Date**: 2026-07-25
- **Status**: Accepted

## Context

w2s hatte zwei gleichwertige UIs über derselben Anwendungsschicht: die server-gerenderte
Thymeleaf-UI und die Angular-SPA (ADR 0001). Beide wurden bei jeder Feature-Änderung parallel
gepflegt (zuletzt die per-Benutzer-Watchlist, ADR 0007) — doppelter Aufwand, doppelte Testfläche.
Die Angular-SPA ist inzwischen funktional vollständig (Katalog, Provider, Watchlist-Import,
Cache-Management, Benutzerverwaltung, Auth-aware Navigation). Zudem ist perspektivisch ein
externer OIDC-Provider (z. B. Keycloak) geplant, bei dem das Login ohnehin beim IdP liegt — ein
eigener Login-Dialog wäre Wegwerf-Code.

## Decision

Die Thymeleaf-UI wird entfernt; die **Angular-SPA ist die einzige UI**. Als einzige
server-gerenderte Seite bleibt die **Login-Seite** erhalten — sie ist der Auth-Einstieg und
bereits OIDC-fertig (der „Sign in with Google"-Button ist dasselbe Muster wie ein künftiger
Keycloak-Button).

- **Entfernt:** die server-gerenderten Anwendungsseiten (Katalog/Provider/Manage/Admin/Watchlist/
  Status-View) samt ihrer `@Controller`, `CommonAttributeService`, `ThymeleafConfig`, alle
  Templates außer `login.html`, sowie die veralteten GET-mit-Nebenwirkung-Wartungsendpunkte
  (`/pre-cache`, `/check-pre-cache`, `/refresh/**`) — damit ist der Rest von TODO-5 erledigt (es
  gibt keine mutierenden GETs mehr; die Wartung läuft ausschließlich über `POST /api/**`).
- **Behalten/umgestellt:** `login.html` + `LoginController`; `StatusController` liefert
  `/public/status` jetzt als **JSON** (öffentlicher Health-Probe), die SPA liest denselben Stand
  über `/api/status`; `SpaController` leitet die Wurzel `/` auf `/app/` um.
- **SecurityConfig vereinfacht** auf das SPA-only-Modell: öffentlich sind nur `/login` (+ dessen
  Bootstrap-CSS via `/webjars/**`) und `/public/**`; `ADMIN` nur noch für `/api/admin/**`,
  `/api/manage/**`, `/api/cache/**` und `POST /api/refresh`; `/api/**` antwortet mit **401**, alle
  anderen (Browser-)Requests werden auf `/login` **weitergeleitet**. Die vielen Legacy-Matcher
  (`/admin/**`-Seiten, `/pre-cache`, `/manage`, `/css //js` …) entfallen.
- **Build entschlackt:** die Thymeleaf-Dialekte (`thymeleaf-extras-springsecurity6`,
  `thymeleaf-layout-dialect`) und die nur von der alten Navigation genutzten Webjars
  (jQuery/Popper/Font-Awesome) fliegen raus; `spring-boot-starter-thymeleaf` und das
  Bootstrap-Webjar bleiben allein für die Login-Seite.

## Consequences

**Einfacher / besser:**

- Nur noch eine UI zu pflegen und zu testen; kein „beide Clients synchron halten" mehr (korrigiert
  den „beide Clients"-Teil von ADR 0006 und 0007).
- Deutlich kleinere, verständlichere `SecurityConfig`; TODO-5 vollständig geschlossen (keine
  mutierenden GETs mehr).
- Kleinere Angriffs- und Abhängigkeitsfläche (weniger Templates, weniger Webjars).

**Schwieriger / Nachteile:**

- Ohne aktiviertes JavaScript gibt es keine UI mehr (die SPA ist Pflicht).
- Server-seitige Autorisierung kann einzelne SPA-Routen nicht unterscheiden: wegen Hash-Routing
  (ADR 0001) kommt jede SPA-Navigation als `GET /app/` an. Absicherbar ist nur die **ganze
  SPA-Shell** (`/app/**` erfordert Login); Fein-Autorisierung passiert clientseitig (Route-Guard)
  plus serverseitig pro `/api/**`-Endpunkt. Der einzige öffentlich erreichbare Stand ist der
  `/public/status`-Health-Probe.
- Die Login-Seite bleibt vorerst ein Thymeleaf-Template; sie entfällt (oder wird zur reinen
  Weiterleitung), sobald Keycloak/OIDC den Login-Flow übernimmt.

## Alternatives Considered

- **Login in die SPA verlagern (eigenes Angular-Login-Formular):** verworfen — mit anstehendem
  OIDC/Keycloak wäre ein selbstgebautes Formular Wegwerf-Code; die vorhandene Login-Seite ist der
  passendere, IdP-fertige Übergang.
- **SPA komplett öffentlich, nur `/api/**` absichern:** verworfen für den Interim — bräuchte HTTP
  Basic als einzigen Login-Weg (schlechtere UX) statt des Formular-Logins; das gewünschte Modell
  ist „alles hinter Login, Redirect zum (künftigen) IdP".
- **Beide UIs behalten:** verworfen — der doppelte Pflege-/Testaufwand ist der Auslöser dieser
  Entscheidung.
