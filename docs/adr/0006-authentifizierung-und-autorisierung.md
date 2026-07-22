# 0006. Authentifizierung & Autorisierung (Spring Security)

- **Date**: 2026-07-22
- **Status**: Accepted

## Context

w2s hatte keinerlei Authentifizierung; die zustandsändernden Wartungs-Endpunkte waren
unauthentifizierte GETs (siehe TODO-5). Beide Clients (Thymeleaf-UI und Angular-SPA) sollen
abgesichert werden, mit lokaler Benutzerverwaltung und optionalem SSO-Login.

## Decision

Spring Security 7 (Boot 4) mit einem **datenbankgestützten Benutzerstore**:

- **Login-Wege:** Formular-Login **und** HTTP Basic über lokale Konten (BCrypt via
  `DelegatingPasswordEncoder`), plus **optionaler Google-OIDC-Login** (nur aktiv, wenn eine
  Client-Registrierung konfiguriert ist — Profil `google` + `GOOGLE_CLIENT_ID/SECRET`; ohne das
  läuft die App auf lokalen Konten). OIDC-Logins werden per E-Mail auf lokale Konten gemappt und
  beim ersten Login als `USER` provisioniert.
- **Rollen:** `USER`, `ADMIN`. **Zugriffsmodell:** alles erfordert Login; zustandsändernde /
  Wartungs-Endpunkte (pre-cache, refresh, invalidate, scrape, list-change) und die
  Benutzerverwaltung erfordern `ADMIN` — behebt TODO-5. Methodensicherheit (`@PreAuthorize`) auf
  `UserAdminService` als Defense-in-depth.
- **Benutzerstore:** `AppUser` + Rollen (Liquibase-Changelog 003), `AppUserDetailsService`; ein
  Initial-Admin wird beim leeren User-Table geseedet (Passwort aus
  `w2s.security.initial-admin.password`, sonst generiert und einmalig geloggt).
- **CSRF:** Cookie-basiert (`CookieCsrfTokenRepository`) mit SPA-Handler + Cookie-Filter. Die
  Angular-API-Basis ist eine **relative** URL (`../api/`), damit Angulars eingebauter
  XSRF-Interceptor das `X-XSRF-TOKEN`-Header setzt.
- **API vs. Seiten:** `/api/**` antwortet unauthentifiziert mit **401** (die SPA leitet dann zur
  Login-Seite), Seiten-Requests im Browser werden auf `/login` **weitergeleitet**.
- **Benutzerverwaltung** in **beiden** Clients: Thymeleaf (`/admin/users`) und Angular
  (`/admin/users`, Route-Guard), beide über dieselbe `UserAdminService`/`/api/admin/users`.

## Consequences

**Einfacher / besser:**

- TODO-5 gelöst: keine ungeschützten mutierenden Endpunkte mehr.
- Ein Benutzer-/Rollenmodell für beide Clients und beide Login-Wege; SSO ohne Zwang.
- Absicherung gegen Selbst-Aussperrung: der letzte aktivierte Admin kann nicht demotiert/deaktiviert/gelöscht werden.

**Schwieriger / Nachteile:**

- Der Google-OIDC-Flow ist in der Sandbox nicht end-to-end testbar (kein echter IdP);
  abgedeckt sind Konfiguration + Mapping-Logik, nicht der Live-Redirect.
- Die Wartungs-Endpunkte bleiben teils GET-mit-Nebenwirkung (jetzt ADMIN-geschützt); die
  saubere Verb-Korrektur (POST) bleibt offen (TODO-5 Rest).
- Die Application-Schicht darf jetzt Repositories nutzen (`UserAdminService`) — die
  ArchUnit-Schichtregel wurde entsprechend gelockert (Repositories sind der Port der
  Use-Case-Schicht).

## Alternatives Considered

- **Resource-Server (JWT-Bearer) statt Session-Login:** verworfen — die App ist eine
  session-basierte Web-App mit Server-gerendertem Login; Bearer-Tokens hätten einen
  Token-Aussteller/-Flow erfordert ohne Mehrwert für die interne UI.
- **Nur OIDC (kein lokaler Store):** verworfen — es wird lokale Benutzerverwaltung ohne
  Zwang zu einem externen IdP gewünscht.
- **In-Memory-User:** verworfen — Benutzerverwaltung braucht Persistenz.
