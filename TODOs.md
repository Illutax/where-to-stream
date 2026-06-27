# TODOs / Bug-Tickets

Backlog aus dem Code-Review vom 2026-06-27. Erledigte Punkte aus dem Review
(#2 Transaktionen, #3 UTF-8, #13 N+1 auf der Startseite, #15 Parser-/Reader-Tests)
sind bereits umgesetzt und stehen hier nicht mehr.

Priorität: 🔴 hoch · 🟠 mittel · 🟢 niedrig

---

## Bugs / Korrektheit

### 🔴 TODO-1 — `ImdbApiClient.search()` ist kaputt / ungenutzt
`services/ImdbApiClient.java`: lädt das Dokument mit `connect.get()` und gibt dann
hart `return null;` zurück. Die Klasse ist außerdem kein Spring-Bean (kein `@Service`)
und wird nirgends produktiv verwendet.
- **Akzeptanzkriterium:** Entweder das Parsen der IMDb-Listenseite korrekt implementieren
  (Rückgabe `List<SearchResult>` statt `null`) **oder** Klasse + zugehörigen Test löschen.
- **Anmerkung:** Der bestehende `ImdbApiClientTest` macht einen echten Netzwerk-Aufruf
  gegen imdb.com ohne Assertion und schlägt in der Sandbox am Egress-Proxy (403) fehl.
  Beim Aufräumen mitnehmen (durch einen Test gegen ein gespeichertes HTML-Fixture ersetzen).

### 🟢 TODO-2 — Tabellen-/Spalten-Tippfehler
- `persistence/QueryMeta.java`: `@Table(name = "QeryMeta")` → `QueryMeta`.
- `persistence/QueryResultDB.java`: `query_result_availablilities` → `query_result_availabilities`.
- **Achtung:** Schema-Migration nötig (siehe TODO-10), `ddl-auto=update` benennt
  Tabellen nicht automatisch um → bestehende Daten gehen sonst verloren.

### 🟠 TODO-3 — Irreführender Join-Spaltenname
`persistence/QueryResultDB.java`: `@CollectionTable(joinColumns = @JoinColumn(name = "imdb_id"))`
joint tatsächlich auf die UUID-PK von `QueryResultDB`, nicht auf eine IMDb-ID.
- **Akzeptanzkriterium:** Spalte z. B. in `query_result_id` umbenennen (mit Migration).

---

## Sicherheit

### 🔴 TODO-5 — Zustandsändernde Endpunkte als GET ohne Auth
`/pre-cache`, `/check-pre-cache`, `/refresh/all`, `/refresh/seen` lösen teure
Remote-Crawls aus, sind per GET erreichbar und damit von Crawlern/Prefetch triggerbar.
- **Akzeptanzkriterium:** Auf `POST` umstellen; Endpunkte hinter Authentifizierung
  legen (Spring Security ergänzen — die App ist aktuell komplett offen).

---

## Architektur / Design

### 🟠 TODO-6 — Controller ruft Controller
`web/ChangeListController.java` injiziert `rest/PreCacheController` und ruft
`cacheController.cache()`.
- **Akzeptanzkriterium:** Cache-Logik in einen `PreCacheService` extrahieren, den
  beide Controller verwenden.

### 🟠 TODO-7 — Verstreute Konfiguration per `@Value`
`wer-streamt.path` wird in `ExportReader` und `FileUtils` separat injiziert,
`wer-streamt.invalidate.after-days` in `StreamInfoService`.
- **Akzeptanzkriterium:** In ein `@ConfigurationProperties`-Record `WerStreamtProperties`
  bündeln. Damit entfällt auch die fragile `@Value`-Field-Injection in `FileUtils`
  (wird in `JpaConfig` per `new FileUtils()` erzeugt).

### 🟠 TODO-8 — `ImdbEntryRepository` ist nicht thread-safe
`services/ImdbEntryRepository.java`: In-Memory-Store auf `HashMap`, wird aber während
laufender `parallelStream`-Requests via `clear()`/`init()` aus `ChangeListController`
neu befüllt → Race-Potenzial.
- **Akzeptanzkriterium:** `ConcurrentHashMap` + atomarer Austausch der Maps beim Reload,
  oder Reload synchronisieren.

### 🟢 TODO-9 — Robustes Scraping (NPE-Schutz)
`services/WerStreamtEsApiClient.java`: `selectFirst(...).childNode(0)` u. ä. ohne
Null-Checks; eine Layout-Änderung bei werstreamt.es kann NPEs auslösen. (Review-Punkt #4,
Korrektheit; wurde aber bewusst zurückgestellt.)
- **Akzeptanzkriterium:** Null-Guards + try/catch pro Eintrag, damit ein fehlerhafter
  Eintrag nicht den ganzen Lauf abbricht. Veralteter User-Agent (Firefox 2.0.0.6, 2007)
  aktualisieren.

### 🟢 TODO-10 — Schema-Versionierung statt `ddl-auto=update`
`application.properties`: `spring.jpa.hibernate.ddl-auto=update`.
- **Akzeptanzkriterium:** Flyway oder Liquibase einführen für reproduzierbare,
  versionierte Schemata (Voraussetzung für TODO-2 und TODO-3).

---

## Performance

### 🟠 TODO-11 — Mehrfache Voll-Auflösung pro Seitenaufruf
`services/AggregateService.java`: `getAll()` löst alle Einträge sequenziell auf.
Die Amazon-Seite (`web/DataAggregateController.getAmazon`) ruft `included()` **und**
`paid()` auf → `getAll()` läuft **zweimal** pro Request.
- **Akzeptanzkriterium:** `getAll()` einmal aufrufen und beide Filter auf das Ergebnis
  anwenden.

### 🟢 TODO-12 — Durchgängiges `FetchType.EAGER`
`persistence/QueryMeta.java` (`@OneToMany`) und `QueryResultDB.java` (`@ElementCollection`)
laden alles eager.
- **Akzeptanzkriterium:** Auf LAZY umstellen und gezielte Fetch-Joins/Queries einsetzen,
  wo nötig.

---

## Build / Betrieb

### 🔴 TODO-13 — Cron zieht Pre-Release-Spring-Boot
Git-History: `4.1.0-M1 → M2 → M3 → M4 → RC1 → 4.1.0`. `upgrade-spring-boot.sh` nutzt
`versions:update-parent` ohne `-DallowSnapshots=false` und ohne Milestone-Filter, d. h.
der Cron deployt automatisch Milestones/RCs in den Betrieb.
- **Akzeptanzkriterium:** Auf stabile Releases beschränken (Ruleset/Rules bzw. passende
  `versions`-Flags), keine Milestones/RCs automatisch.

### 🟢 TODO-14 — `versions-maven-plugin` ohne Version
`pom.xml`: Plugin ohne fixierte `<version>`.
- **Akzeptanzkriterium:** Version festnageln für reproduzierbare Builds.

### 🟢 TODO-15 — Port-Inkonsistenz dokumentieren/vereinheitlichen
`server.port=8001` (properties), `EXPOSE 8080` (Dockerfile), `SERVER_PORT=8080` (compose).
Funktioniert, weil compose überschreibt.
- **Akzeptanzkriterium:** Werte angleichen oder in der README erklären.

### 🟢 TODO-16 — README fehlt
Kein Setup-Dokument vorhanden.
- **Akzeptanzkriterium:** README mit Setup (CSV in `assets/` ablegen, Profile, Port,
  verfügbare Endpunkte) ergänzen.

---

## Kleinigkeiten

### 🟢 TODO-17 — Aufräumarbeiten
- `configurations/JpaConfig.java`: ungenutzter Import `org.springframework.beans.factory.annotation.Value`.
- `services/WerStreamtEsApiClient.java` (`search`) und `services/ImdbApiClient.java` (`search`):
  String-Konkatenation im Logging (`"Searching for: " + ...`) → parametrisiertes Logging.
- `web/StatusController.java`: `@GetMapping("public/status")` ohne führenden Slash
  (inkonsistent zu den übrigen Mappings).

### 🟢 TODO-18 — `Price` wrappt fehlende Werte statt `null`
`services/WerStreamtEsApiClient.parseAvailability(...)`: fehlende Qualitäten werden als
`new Price(null)` gespeichert, d. h. `availability.sd()` etc. ist nie `null`, sondern ein
Price-Objekt mit `value() == null`. Aufrufer (z. B. `DataAggregateController.prettyPrint`)
prüfen aber auf `a.fourK() != null` — das ist dadurch immer wahr und `value()` kann `null`
ausgegeben werden.
- **Akzeptanzkriterium:** Fehlende Preise konsistent als `null`-`Price` (Optional/echtes
  `null`) modellieren und die Aufrufer entsprechend anpassen. (Beim Code-Review-Test
  #15 aufgefallen.)
