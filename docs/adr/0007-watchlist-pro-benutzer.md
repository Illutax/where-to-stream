# 0007. Watchlist pro Benutzer (DB-gestützt statt globaler Dateiliste)

- **Date**: 2026-07-25
- **Status**: Accepted

## Context

Die Watchlist war bislang **global**: `ImdbCatalog` lud beim Start die
lexikographisch letzte CSV aus dem `assets/`-Verzeichnis in den Speicher, und ein globaler
"selected list"-Zustand (`ListSelectionService`, `/list` bzw. `PUT /api/lists/selection`)
schaltete zwischen den Dateien um. Alle Benutzer sahen dieselbe Liste; das Umschalten war eine
ADMIN-Aktion. Mit der eingeführten Authentifizierung (ADR 0006) soll **jeder Benutzer seine
eigene Liste pflegen**.

## Decision

Die Watchlist wird **pro Benutzer** in der Datenbank gehalten und per Upload gepflegt.

- **Persistenz:** Neue Tabelle `watchlist_entry` (Liquibase 006), Schlüssel `(user_id, imdb_id)`,
  FK auf `app_user` mit `ON DELETE CASCADE`. `WatchlistEntry` ist das JPA-Entity.
- **Schichten:** `ImdbCatalog` (global, in-memory) wird durch `WatchlistCatalog` (DB,
  `userId`-skopiert) ersetzt. Alle Lese-/Query-Methoden in der Services- und Application-Schicht
  nehmen jetzt eine `UUID userId`. Die `SecurityContext`/der Benutzername wird **nur in der
  Präsentationsschicht** gelesen; `CurrentUserService` übersetzt den authentifizierten
  Benutzernamen in die `userId`, damit die darunterliegenden Schichten Spring Security nicht
  sehen.
- **Import:** Statt Dateien im `assets/`-Verzeichnis lädt der Benutzer seinen IMDb-CSV-Export
  über die UI hoch (`/watchlist`, `/api/watchlist/import`, multipart). `ExportReader` parst einen
  `InputStream` (kein Dateipfad mehr). `WatchlistImportService` führt einen **Full-Sync** durch:
  neue Titel werden angelegt, geänderte aktualisiert, im Upload fehlende entfernt. Der Import ist
  bewusst eine reine DB-Operation (kein synchrones Scraping unter der Transaktion) — Titel werden
  beim ersten Seitenaufruf lazy gegen den Cache aufgelöst.
- **Globaler Cache bleibt global:** Der werstreamt.es-Cache (`query_meta`) ist pro IMDb-Id
  geschlüsselt und **über alle Benutzer geteilt**. Pre-Cache/Refresh/Manage arbeiten auf der
  Vereinigung aller Watchlists (distinct `imdbId`); ein Titel gilt als "seen", wenn ihn ein
  beliebiger Benutzer bewertet hat.
- **Autorisierung:** Die Watchlist ist für **jeden angemeldeten Benutzer** zugänglich; die
  ADMIN-Beschränkung entfällt für die (nun entfernten) Listen-Endpunkte. Cache-Wartung bleibt
  ADMIN.
- **Beide Clients:** Thymeleaf (`/watchlist`) und Angular (`/app/#/watchlist`,
  `WatchlistImportPage` + `WatchlistApi` + `WatchlistStore`) bieten Upload/Status/Clear; die
  Navigation zeigt "My list: N titles" statt der ausgewählten Liste. Die alten Artefakte
  (`change-list`, `list-picker`, `ListsApi`, `ListSelectionStore`) sind entfernt.

## Consequences

**Einfacher / besser:**

- Echte Mehrbenutzer-Nutzung: jede Person pflegt ihre eigene Liste, ohne sich gegenseitig zu
  überschreiben.
- Kein `assets/`-Volume und kein Dateisystem-Zustand mehr — Import läuft über die
  authentifizierte UI; der `assets`-Mount entfällt aus `compose.yml`.
- Isolation ist auf Datenebene erzwungen (alle Queries `userId`-skopiert) und durch
  Repository-Tests (H2 + Testcontainers-MariaDB) abgesichert.

**Schwieriger / Nachteile:**

- Zusätzliche Grenze `username → userId`: die Präsentationsschicht muss den authentifizierten
  Benutzer auflösen und weiterreichen; vergessene Skopierung wäre ein Daten-Leck zwischen
  Benutzern (durch Tests abgedeckt).
- Der Import ist ein Full-Sync (löscht im Upload fehlende Titel) — das ist bewusst, aber
  destruktiver als ein reines Hinzufügen; die UI weist darauf hin.
- Bestehende Deployments verlieren die alte globale Dateiliste; Benutzer müssen ihren CSV-Export
  einmalig hochladen.

## Alternatives Considered

- **Globale Liste beibehalten, nur pro Benutzer filtern:** verworfen — es gibt keinen sinnvollen
  Filter, jede Person hat eine andere Liste.
- **Upload weiter als Dateien im `assets/`-Verzeichnis (pro Benutzer ein Unterordner):**
  verworfen — Dateisystem-Zustand im Container ist bei rootless-Podman/SELinux und beim
  cron-Neustart-Deployment fragil; die DB ist ohnehin vorhanden und wird gesichert.
- **Import synchron pre-cachen:** verworfen — würde den Upload lange blockieren und werstreamt.es
  unter Last setzen; lazy Auflösung beim Seitenaufruf reicht.
