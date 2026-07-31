# TODOs / Bug-Tickets

Backlog aus dem Code-Review vom 2026-06-27.

Priorität: 🔴 hoch · 🟠 mittel · 🟢 niedrig · Status: ✅ erledigt · ⬜ offen

---

## ✅ Erledigt

Review-Punkte (eigene Nummerierung des Reviews, nicht TODO-N):

- ✅ **#2** — Wirkungsloses `@Transactional` über `parallelStream` behoben (`440b810`).
- ✅ **#3** — CSV-Import liest jetzt explizit UTF-8 (`d85ad4d`).
- ✅ **#13** — N+1 auf der Startseite per Batch-Query beseitigt (`31f56d9`).
- ✅ **#15** — Tests für CSV-Import und werstreamt.es-Parsing ergänzt (`8e20cdf`);
  Assertions konsolidiert (`8498adf`).

Größere Umbauten (nach dem Review):

- ✅ **Watchlist pro Benutzer** — die globale, dateibasierte Liste (`ImdbCatalog` +
  `ListSelectionService`, `assets/`-Verzeichnis) ist durch eine **DB-gestützte Watchlist pro Benutzer**
  ersetzt: neue Tabelle `watchlist_entry`, `WatchlistCatalog`/`WatchlistImportService`,
  CSV-Upload statt Datei-Ablage, Full-Sync-Import.
  `CurrentUserService` überbrückt `username → userId`; der werstreamt.es-Cache bleibt global.
  Beide Clients (Thymeleaf + Angular) bekamen eine `/watchlist`-Seite; die Listen-Auswahl-Artefakte
  wurden entfernt.
  Siehe [ADR 0007](docs/adr/0007-watchlist-pro-benutzer.md).
- ✅ **Thymeleaf-Client entfernt** — die Angular-SPA ist die einzige UI; alle server-gerenderten
  Anwendungsseiten + Controller, die Legacy-GET-Wartungsendpunkte und die Thymeleaf-Dialekte
  wurden entfernt (nur die Login-Seite bleibt als OIDC-fertiger Auth-Einstieg).
  `SecurityConfig` entsprechend eingedampft.
  Siehe [ADR 0008](docs/adr/0008-thymeleaf-client-entfernen.md).
- ✅ **Domain-Values statt Primitiver** — `ImdbId`, `ReleaseYear`, `WatchlistDate` als Value Objects
  im Backend (Jackson `@JsonValue` + JPA-`@Converter`, JSON/DB-Verträge unverändert) und
  als Branded Types im Angular-Client.
  Siehe [ADR 0009](docs/adr/0009-domainvalues-statt-primitiven.md).

TODO-Tickets:

- ✅ **TODO-2** — Tabellen-/Spalten-Tippfehler (`QueryMeta`, `query_result_availabilities`).
- ✅ **TODO-3** — Irreführende Join-Spalte → `query_result_id`.
- ✅ **TODO-10 / TODO-27** — Liquibase eingeführt, Schema als versioniertes Changelog,
  `ddl-auto=validate`.
- ✅ **TODO-6** — Cache-Logik in `PreCacheService` extrahiert;
  Controller hängen nicht mehr voneinander ab.
- ✅ **TODO-7** — `wer-streamt.*` in `WerStreamtProperties` (`@ConfigurationProperties`) gebündelt;
  `@Value`-Field-Injection entfernt.
- ✅ **TODO-8** — `ImdbEntryRepository` nutzt einen unveränderlichen Snapshot hinter `AtomicReference`
  (lock-freie Reads, atomarer Reload).
- ✅ **TODO-9** — Robustes Scraping: Null-Guards + try/catch pro Provider, Spalten-Anzahl vor Zugriff geprüft;
  aktueller User-Agent.
- ✅ **TODO-18** — Fehlende Qualitäten sind jetzt `null`-`Price` statt `new Price(null)`;
  `prettyPrint`-Null-Checks greifen dadurch korrekt.
- ✅ **TODO-17** — Aufräumarbeiten: ungenutzter `@Value`-Import (in TODO-7),
  parametrisiertes Logging (`WerStreamtEsApiClient` in TODO-9, `ImdbApiClient`),
  `@GetMapping("/public/status")`.
- ✅ **TODO-30/31/32/33/36/37** — Architektur-Konsolidierung (`domain/`-Paket, `ImdbCatalog`,
  Provider-Handler, Tx-Grenzen, `StreamAvailabilityProvider`, flaches `getAll`).
- ✅ **TODO-35/38/39** — `invalidated`-Flag aktiviert:
  Einträge invalidieren + nur invalidierte/fehlende scrapen (`/manage`-UI).
- ✅ **TODO-40** — Liquibase-Changesets auf XML umgestellt (portables Schema, `ddl-auto=none`).
- ✅ **TODO-41** — MariaDB als First-Class-DB (Profil `mariadb`, Treiber, compose-Service);
  Repository-Tests laufen zusätzlich gegen eine Testcontainers-MariaDB.
- ✅ **TODO-1** — Entfernt ImdbApiClient.
---

## Bugs / Korrektheit

### ✅ TODO-1 — `ImdbApiClient.search()` ist kaputt / ungenutzt
`services/ImdbApiClient.java`: lädt das Dokument mit `connect.get()`
und gibt dann hart `return null;` zurück.
Die Klasse ist außerdem kein Spring-Bean (kein `@Service`) und wird nirgends produktiv verwendet.
- **Akzeptanzkriterium:** Entweder das Parsen der IMDb-Listenseite korrekt implementieren
  (Rückgabe `List<SearchResult>` statt `null`) **oder** Klasse + zugehörigen Test löschen.
- **Anmerkung:** Der bestehende `ImdbApiClientTest` macht einen echten Netzwerk-Aufruf gegen imdb.com ohne Assertion
  und schlägt in der Sandbox am Egress-Proxy (403) fehl.
  Beim Aufräumen mitnehmen (durch einen Test gegen ein gespeichertes HTML-Fixture ersetzen).

### ✅ TODO-2 — Tabellen-/Spalten-Tippfehler
- `persistence/QueryMeta.java`: `@Table(name = "QeryMeta")` → `QueryMeta`.
- `persistence/QueryResultDB.java`: `query_result_availablilities` → `query_result_availabilities`.
- **Achtung:** Schema-Migration nötig (siehe TODO-10), `ddl-auto=update` benennt Tabellen nicht automatisch um
  → bestehende Daten gehen sonst verloren.
- **Erledigt:** Entity-Annotationen korrigiert;
  das korrigierte Schema steckt im Liquibase-Baseline (TODO-27).

### ✅ TODO-3 — Irreführender Join-Spaltenname
`persistence/QueryResultDB.java`: `@CollectionTable(joinColumns = @JoinColumn(name = "imdb_id"))`
joint tatsächlich auf die UUID-PK von `QueryResultDB`, nicht auf eine IMDb-ID.
- **Akzeptanzkriterium:** Spalte z. B. in `query_result_id` umbenennen (mit Migration).
- **Erledigt:** Join-Spalte heißt jetzt `query_result_id`; Schema via Liquibase (TODO-27).

---

## Sicherheit

### ✅ TODO-5 — Zustandsändernde Endpunkte als GET ohne Auth
`/pre-cache`, `/check-pre-cache`, `/refresh/all`, `/refresh/seen` lösten teure Remote-Crawls aus,
waren per GET erreichbar und damit von Crawlern/Prefetch triggerbar.
- **Akzeptanzkriterium:** Auf `POST` umstellen;
  Endpunkte hinter Authentifizierung legen (Spring Security ergänzen — die App war komplett offen).
- **Erledigt (Auth):** Spring Security ergänzt ([ADR-0006](docs/adr/0006-authentifizierung-und-autorisierung.md));
  die neue REST-API nutzt korrekte Verben (`POST /api/refresh`, `POST /api/cache`, …).
- **Erledigt (Verben):** Mit dem Entfernen des Thymeleaf-Clients
  ([ADR-0008](docs/adr/0008-thymeleaf-client-entfernen.md)) wurden die Legacy-GET-Endpunkte
  (`/pre-cache`, `/check-pre-cache`, `/refresh/**`) gelöscht — es gibt keine mutierenden GETs mehr;
  Wartung läuft ausschließlich über `POST /api/**` (ADMIN).

---

## Architektur / Design

### ✅ TODO-6 — Controller ruft Controller
`web/ChangeListController.java` injizierte `rest/PreCacheController` und rief
`cacheController.cache()`.
- **Akzeptanzkriterium:** Cache-Logik in einen `PreCacheService` extrahieren, den
  beide Controller verwenden.
- **Erledigt:** `PreCacheService.cacheAll()` / `findUncached()` eingeführt;
  `PreCacheController` und `ChangeListController` nutzen den Service.

### ✅ TODO-7 — Verstreute Konfiguration per `@Value`
`wer-streamt.path` wurde in `ExportReader` und `FileUtils` separat injiziert,
`wer-streamt.invalidate.after-days` in `StreamInfoService`.
- **Akzeptanzkriterium:** In ein `@ConfigurationProperties`-Record `WerStreamtProperties`
  bündeln. Damit entfällt auch die fragile `@Value`-Field-Injection in `FileUtils`
  (wird in `JpaConfig` per `new FileUtils()` erzeugt).
- **Erledigt:** `WerStreamtProperties` (mit `Invalidate.afterDays`, Default 28) per
  `@ConfigurationPropertiesScan` aktiviert. `FileUtils` ist jetzt `@Component` mit
  Konstruktor-Injection; `JpaConfig` injiziert es, statt `new FileUtils()` zu bauen.

### ✅ TODO-8 — `ImdbEntryRepository` ist nicht thread-safe
`services/ImdbEntryRepository.java`: In-Memory-Store auf `HashMap`, wurde aber während
laufender `parallelStream`-Requests via `clear()`/`init()` aus `ChangeListController`
neu befüllt → Race-Potenzial.
- **Akzeptanzkriterium:** `ConcurrentHashMap` + atomarer Austausch der Maps beim Reload,
  oder Reload synchronisieren.
- **Erledigt:** Gesamter Zustand (beide Maps + Listenname) als unveränderliches `State`-Record
  hinter einer `AtomicReference`; `init`/`clear` tauschen den Snapshot atomar, Reads sind
  lock-frei und konsistent.

### ✅ TODO-9 — Robustes Scraping (NPE-Schutz)
`services/WerStreamtEsApiClient.java`: `selectFirst(...).childNode(0)` u. ä. ohne Null-Checks;
eine Layout-Änderung bei werstreamt.es konnte NPEs auslösen.
(Review-Punkt #4, Korrektheit; wurde aber bewusst zurückgestellt.)
- **Akzeptanzkriterium:** Null-Guards + try/catch pro Eintrag,
  damit ein fehlerhafter Eintrag nicht den ganzen Lauf abbricht.
  Veralteter User-Agent (Firefox 2.0.0.6, 2007) aktualisieren.
- **Erledigt:** `parseProvider` kapselt jeden Provider in try/catch
  und prüft die Spalten-Anzahl vor dem Indexzugriff;
  `qualityLabel`/`priceText` und `toSearchResult` sind null-sicher; User-Agent auf aktuellen Chrome aktualisiert.
  Tests `skipsProviderWithUnexpectedColumnCount` / `skipsMalformedEmWithoutCrashing` ergänzt.

### ✅ TODO-10 — Schema-Versionierung statt `ddl-auto=update`
`application.properties`: `spring.jpa.hibernate.ddl-auto=update`.
- **Akzeptanzkriterium:** Flyway oder Liquibase einführen für reproduzierbare,
  versionierte Schemata (Voraussetzung für TODO-2 und TODO-3).
- **Erledigt:** Über TODO-27 (Liquibase) umgesetzt; `ddl-auto=validate`.

---

## Performance

### ✅ TODO-11 — Mehrfache Voll-Auflösung pro Seitenaufruf
`services/AggregateService.java`: `getAll()` löst alle Einträge sequenziell auf.
Die Amazon-Seite (`web/DataAggregateController.getAmazon`) rief `included()` **und** `paid()` auf
→ `getAll()` lief **zweimal** pro Request.
- **Akzeptanzkriterium:** `getAll()` einmal aufrufen und beide Filter auf das Ergebnis anwenden.
- **Erledigt:** `AggregateService.contentFor(serviceName)` löst einmal auf und liefert `included` + `paid`
  (Record `ServiceContent`); die Amazon-Seite nutzt das.

### 🟢 TODO-12 — Durchgängiges `FetchType.EAGER`
`persistence/QueryMeta.java` (`@OneToMany`) und `QueryResultDB.java` (`@ElementCollection`) laden alles eager.
- **Akzeptanzkriterium:** Auf LAZY umstellen und gezielte Fetch-Joins/Queries einsetzen,
  wo nötig.

---

## Build / Betrieb

### 🔴 TODO-13 — Cron zieht Pre-Release-Spring-Boot
Git-History: `4.1.0-M1 → M2 → M3 → M4 → RC1 → 4.1.0`.
`upgrade-spring-boot.sh` nutzt `versions:update-parent` ohne `-DallowSnapshots=false` und ohne Milestone-Filter,
d. h. der Cron deployt automatisch Milestones/RCs in den Betrieb.
- **Akzeptanzkriterium:** Auf stabile Releases beschränken (Ruleset/Rules bzw. passende `versions`-Flags),
  keine Milestones/RCs automatisch.

### 🟢 TODO-14 — `versions-maven-plugin` ohne Version
`pom.xml`: Plugin ohne fixierte `<version>`.
- **Akzeptanzkriterium:** Version festnageln für reproduzierbare Builds.

### ✅ TODO-15 — Port-Inkonsistenz dokumentieren/vereinheitlichen
`server.port=8001` (properties), `EXPOSE 8080` (Dockerfile), `SERVER_PORT=8080` (compose).
Funktioniert, weil compose überschreibt.
- **Akzeptanzkriterium:** Werte angleichen oder in der README erklären.
- **Erledigt:** Werte bleiben bewusst unterschiedlich (Compose überschreibt),
  sind aber jetzt in der README-Konfigurationstabelle dokumentiert (`server.port` → „HTTP port (Docker overrides to 8080)").

### ✅ TODO-16 — README fehlt
Kein Setup-Dokument vorhanden.
- **Akzeptanzkriterium:** README mit Setup (CSV in `assets/` ablegen, Profile, Port,
  verfügbare Endpunkte) ergänzen.
- **Erledigt:** Umfassende README (Setup, Prerequisites, Profile inkl. `mariadb`/`google`,
  Konfigurationstabelle, vollständige `## Endpoints`-Übersicht).

---

## Kleinigkeiten

### ✅ TODO-17 — Aufräumarbeiten
- `configurations/JpaConfig.java`: ungenutzter Import `org.springframework.beans.factory.annotation.Value`.
  → in TODO-7 entfernt (Klasse umgebaut).
- `services/WerStreamtEsApiClient.java` (`search`) und `services/ImdbApiClient.java` (`search`):
  String-Konkatenation im Logging (`"Searching for: " + ...`) → parametrisiertes Logging.
  → erledigt (WerStreamtEsApiClient in TODO-9, ImdbApiClient hier).
- `web/StatusController.java`: `@GetMapping("public/status")` ohne führenden Slash
  (inkonsistent zu den übrigen Mappings). → erledigt: `@GetMapping("/public/status")`.

### ✅ TODO-18 — `Price` wrappt fehlende Werte statt `null`
`services/WerStreamtEsApiClient.parseAvailability(...)`: fehlende Qualitäten werden als `new Price(null)` gespeichert,
d. h. `availability.sd()` etc. ist nie `null`, sondern ein Price-Objekt mit `value() == null`.
Aufrufer (z. B. `DataAggregateController.prettyPrint`) prüfen aber auf `a.fourK() != null`
— das ist dadurch immer wahr und `value()` kann `null` ausgegeben werden.
- **Akzeptanzkriterium:** Fehlende Preise konsistent als `null`-`Price` (Optional/echtes `null`)
  modellieren und die Aufrufer entsprechend anpassen.
  (Beim Code-Review-Test #15 aufgefallen.)
- **Erledigt:** `priceOrNull(...)` liefert `null` für nicht angebotene Qualitäten;
  `prettyPrint` (das bereits auf `!= null` prüft) gibt damit keine `null`-Werte mehr aus.

---

## Aus dem Re-Scan (2026-06-27, nach Umsetzung von TODO-6/7/8/9/17/18)

### ✅ TODO-19 — `/query` umgeht den Cache
`rest/QueryController.query(...)` ruft `werStreamtEsApiClient.query(...)` **direkt** auf
und scrapet damit bei jedem Aufruf live, während `/search` über `StreamInfoService` (gecacht) geht.
Inkonsistent und teuer.
- **Akzeptanzkriterium:** `/query` ebenfalls über `StreamInfoService.resolve(...)` laufen lassen
  (oder den Endpunkt entfernen, falls redundant zu `/search`).
- **Erledigt (obsolet):** `QueryController`/`/query` existiert nicht mehr.
  Der heutige Lookup-by-id-Endpunkt `GET /api/search?imdbId=` (`SearchApiController` →
  `SearchService.resolveByImdbId` → `StreamInfoService.resolve`) läuft bereits über den Cache.

### ✅ TODO-20 — Kein zentrales Fehler-Handling
Scraping-/IO-Fehler wurden in `WerStreamtEsApiClient` als nacktes `new RuntimeException(e)` weitergeworfen
und landeten ungefiltert als HTTP 500.
- **Erledigt:** Neue `domain.ScrapingException` (bewusst in `domain`, nicht `services`,
  da `ApiExceptionHandler` in der Presentation-Schicht sonst laut `ArchitectureTest` nicht
  darauf zugreifen dürfte) kapselt den `IOException`-Fall in `search()`/`query()`.
  `ApiExceptionHandler` bildet sie neu auf **502 Bad Gateway** ab.
  Live gegen `mvn spring-boot:run` verifiziert: `GET /api/search?imdbId=tt0111161` lieferte in dieser
  Umgebung einen echten IO-Fehler (Egress-Proxy) und kam sauber als
  `{"status":502,"title":"Upstream lookup failed","detail":"Query for imdbId 'tt0111161' failed"}`
  zurück statt als leeres 500.
  Siehe auch F12 (Validierungsfehler derselben Lücke).

### ✅ TODO-21 — `ExportReader` bricht beim ganzen Import ab, wenn eine Zeile fehlerhaft ist
`services/ExportReader.parse(...)`: `Integer.parseInt(year)` (NumberFormatException) bzw.
`extractImdbId(url)` (IllegalArgumentException) waren nicht pro Zeile abgesichert — eine
einzige kaputte Zeile ließ den gesamten Import (und damit den App-Start) scheitern.
- **Akzeptanzkriterium:** Pro Zeile try/catch, fehlerhafte Zeilen loggen und überspringen
  (analog zur Provider-Robustheit aus TODO-9).
- **Erledigt:** Zeilen-Parsing in `toEntry(...)` extrahiert;
  der Loop fängt `RuntimeException` pro Zeile, loggt die Zeilennummer und überspringt.
  Der id-Zähler läuft nur bei Erfolg weiter (kontinuierliche ids).
  Test `skipsMalformedRowsAndKeepsIdsContiguous` ergänzt.

### 🟢 TODO-22 — Hartkodiertes CSV-Header-Array
`services/ExportReader.headers`: feste Spaltenliste; bricht still, wenn IMDb das Exportformat ändert.
- **Akzeptanzkriterium:** Header aus der Datei lesen
  (`CSVFormat.builder().setHeader().setSkipHeaderRecord(true)`)
  und nur die benötigten Spalten gezielt referenzieren.

### ✅ TODO-23 — `ResponseEntity<?>` mit rohem Wildcard
`rest/QueryController`: `query(...)` und `search(...)` geben `ResponseEntity<?>` zurück —
keine Typsicherheit für die Aufrufer/Tests.
- **Akzeptanzkriterium:** Konkrete Rückgabetypen (`ResponseEntity<List<QueryResult>>` o. ä.).
- **Erledigt (obsolet):** `QueryController` existiert nicht mehr;
  kein Controller im Codebase gibt heute noch `ResponseEntity<?>` zurück (`grep` liefert keine Treffer)
  — alle REST-Controller haben konkrete Rückgabetypen.

### ✅ TODO-24 — Tests für neue/ungetestete Service-Logik fehlen
Nach den Refactorings waren `PreCacheService`, `StreamInfoService.resolveAll(...)`
(Caching/Threshold/Batch-Miss-Fetch) und das atomare Reload-Verhalten von `ImdbEntryRepository`
nicht durch Unit-Tests abgedeckt.
- **Akzeptanzkriterium:** Gezielte Unit-Tests ergänzen (Mockito für die Repos/Clients).
- **Erledigt:** `ImdbEntryRepositoryTest`, `PreCacheServiceTest`, `StreamInfoServiceTest`
  (Cache-Hit/Miss/Expiry/forceRefresh/Batch).
  Beim Schreiben fiel TODO-28 auf.

### ✅ TODO-25 — Aggregat-Seiten berechnen bei jedem Request alles neu
`web/DataAggregateController` + `services/AggregateService`: jede Anbieter-Seite ruft `getAll()` auf
und löste damit sämtliche Einträge sequenziell auf (über TODO-11 hinaus,
das nur den doppelten `getAll()`-Aufruf der Amazon-Seite betrifft).
- **Akzeptanzkriterium:** Aggregat-Ergebnisse cachen/vorberechnen
  bzw. die Batch-Logik aus `resolveAll(...)` (TODO-13/#13) wiederverwenden.
- **Erledigt:** `getAll()` nutzt jetzt `streamInfoService.resolveAll(...)`
  → eine Batch-Query statt N Einzelabfragen.
  (Echtes Aggregat-Caching bleibt als optionale spätere Optimierung offen.)

---

## Aus dem Re-Scan (2026-06-28)

### ✅ TODO-26 — Fehler-Logs ohne Query-Kontext
`services/WerStreamtEsApiClient`: Die `catch`-Blöcke in `query(...)` und `search(...)` loggten
`log.error("Not found %s".formatted(e.getMessage()))` bzw. warfen `new RuntimeException(e)`,
ohne anzugeben, **für welche Query/imdbId** der Fehler auftrat.
Bei den `parallelStream`-Läufen (Pre-Cache/Refresh) war so nicht nachvollziehbar,
welcher Eintrag fehlschlug.
- **Akzeptanzkriterium:** In allen Fehlerausgaben des Clients die betroffene Query
  (imdbId bzw. Suchbegriff) mitloggen.
- **Erledigt:** `query`/`search` loggen bzw. wrappen Fehler jetzt mit imdbId/Suchbegriff.

### ✅ TODO-29 — Requests/Sekunde gegen werstreamt.es begrenzen
Pre-Cache/Refresh feuern via `parallelStream` viele Requests gleichzeitig gegen werstreamt.es
— unhöflich und ein Block-Risiko.
- **Akzeptanzkriterium:** Outbound-Requests drosseln;
  konfigurierbares, sinnvoll vorbelegtes Property.
- **Erledigt:** `RateLimiter` (global, `synchronized`, mindest-Abstand zwischen Requests),
  `WerStreamtEsApiClient.query/search` rufen `acquire()` vor dem HTTP-Get;
  Property `wer-streamt.rate-limit.requests-per-second` (Default `2`, `<= 0` deaktiviert).

### ✅ TODO-28 — `forceRefresh` war invertiert (Refresh refetchte nie)
`services/StreamInfoService.resolve(imdbId, forceRefresh)`: Der Filter lautete `forceRefresh || isFresh(...)`.
Bei `forceRefresh == true` wurde der gecachte Eintrag dadurch **behalten** statt neu geholt
— d. h. die `/refresh/*`-Endpunkte (die `resolve(id, true)` aufrufen) aktualisierten die Daten nie.
- **Akzeptanzkriterium:** `forceRefresh == true` erzwingt einen erneuten Fetch.
- **Erledigt:** Bedingung zu `!forceRefresh && isFresh(...)` korrigiert;
  beim Schreiben der Tests (TODO-24) aufgefallen.
  Test `resolveForceRefreshAlwaysFetches` deckt es ab.

### ✅ TODO-27 — Liquibase einführen und DB-Schema als Changelog ablegen
Das Schema wurde von Hibernate per `ddl-auto=update` verwaltet (siehe auch TODO-10).
- **Akzeptanzkriterium:** Liquibase einbinden, das vollständige Schema als Changelog hinterlegen
  und `ddl-auto` auf `validate` umstellen,
  sodass das Schema reproduzierbar und versioniert ist.
  Dies ist auch die Voraussetzung für die Umbenennungen aus TODO-2/TODO-3.
- **Hinweis:** Die H2-DB hält ausschließlich gecachte Scrape-Ergebnisse;
  das Baseline-Schema geht von einer frischen DB aus (für bestehende Deployments altes `./db` entfernen
  — der Cache füllt sich via `/pre-cache` neu).
- **Erledigt:** `spring-boot-liquibase` ergänzt;
  Baseline-Changelog unter `src/main/resources/db/changelog/`
  (`db.changelog-master.yaml` → `changes/001-baseline-schema.sql`),
  generiert aus dem Hibernate-Schema (inkl. der TODO-2/TODO-3-Namen);
  `ddl-auto=validate` in Haupt- und Test-Konfiguration.
  Tests laufen grün gegen das von Liquibase erzeugte Schema.

---

## Architektur-Review (2026-06-28)

Vollständige Analyse: [`docs/ARCHITECTURE_REVIEW.md`](docs/ARCHITECTURE_REVIEW.md).
Die konkreten, umsetzbaren Punkte daraus:

### ✅ TODO-30 — `entities/` ist irreführend benannt
Das Paket `entities/` enthielt reine Domänen-Records (keine JPA-Entities);
die echten `@Entity`-Klassen liegen in `persistence/`.
- **Akzeptanzkriterium:** `entities/` → `domain/` umbenennen (ggf. `domainvalues/` hineinziehen);
  JPA-Entities bleiben in `persistence/`.
- **Erledigt:** `entities/` und `domainvalues/` zu `domain/` zusammengeführt
  (`ImdbEntry`, `QueryResult`, `SearchResult` + die `@Embeddable`-Werte `Availability`,
  `Price`, Enum `AvailabilityType`).
  JPA-`@Entity`-Klassen bleiben in `persistence/`.

### ✅ TODO-31 — `ImdbEntryRepository` ist kein Repository
Stateful In-Memory-Katalog, benannt wie ein Spring-Data-Repository und in `services/`.
- **Akzeptanzkriterium:** In `ImdbCatalog`/`WatchlistStore` umbenennen,
  klar von den Spring-Data-Repos in `persistence/` abgrenzen.
- **Erledigt:** Klasse → `ImdbCatalog` (Felder/Variablen/Bean-Methode `imdbCatalog`).

### ✅ TODO-32 — Nahezu identische Provider-Handler in `DataAggregateController`
`getDisney`/`getNetflix`/`getWow` (und `getAmazon`/`getGoogle`) unterschieden sich nur durch Service-/View-Namen.
- **Akzeptanzkriterium:** Datengetrieben zusammenfassen (Enum/Map aus Pfad → Service+View),
  ~4 Methoden auf eine reduzieren.
- **Erledigt:** Gemeinsame Helfer `flatratePage(...)`, `sortedByAdded(...)`, `paidDtos(...)`;
  die Handler delegieren nur noch.
  (Explizite Routen beibehalten statt Catch-all-`{path}`,
  um Routing-Mehrdeutigkeit zu vermeiden.)

### ✅ TODO-33 — Transaktionsgrenze auf einem Controller
`DataAggregateController` war `@Transactional(readOnly = true)` auf Klassenebene.
- **Akzeptanzkriterium:** Transaktionsgrenzen in die Service-Schicht verschieben;
  Controller nicht transaktional.
- **Erledigt:** `@Transactional` von `DataAggregateController` **und** `ChangeListController` entfernt.
  DB-Zugriffe laufen über transaktionale Service-Methoden (`StreamInfoService.resolve/resolveAll`);
  die zurückgegebenen Records sind losgelöst, daher kein Open-Session-in-View nötig.
  (Nebeneffekt: Cache-Writes bei Miss laufen jetzt in einer Read-Write-Tx statt in einer Read-only-Tx.)

### ✅ TODO-34 — View-Model-Aufbau im Controller
`IndexDto`, `PaidDto` und `prettyPrint(...)` stecken im `DataAggregateController`.
- **Akzeptanzkriterium:** In einen Assembler/Formatter (oder DTO-Factory-Methoden) auslagern;
  Controller ruft nur noch den Assembler.
- **Erledigt (obsolet):** `DataAggregateController` existiert nicht mehr.
  Die heutigen Controller (`api/CatalogApiController`, `api/ProviderApiController`, …) sind dünn;
  die View-Model-Zusammenstellung sitzt in der Application-Schicht
  (`application/CatalogOverviewService`, `application/ProviderPageService`).

### ✅ TODO-35 — `invalidated`-Flag ist faktisch tot
`QueryMeta.invalidated` wurde nie auf `true` gesetzt, aber überall mitgefiltert.
- **Akzeptanzkriterium:** Invalidierung tatsächlich umsetzen (z. B. beim Refresh alte Zeilen invalidieren)
  **oder** Flag + Query-Suffix entfernen.
- **Erledigt:** Über TODO-38/TODO-39 zum Leben erweckt
  — `invalidateByImdbIds(...)` setzt das Flag;
  invalidierte Einträge gelten als „uncached" und werden gezielt neu gescraped.

### ✅ TODO-36 — Provider-Abstraktion fürs Scraping
Kein Interface über „Stream-Verfügbarkeits-Provider";
fest an jsoup/werstreamt.es gekoppelt (`ImdbApiClient` ist tot, siehe TODO-1).
- **Akzeptanzkriterium:** Interface `StreamAvailabilityProvider`
  (z. B. `List<QueryResult> query(String imdbId)`), implementiert von `WerStreamtEsApiClient`;
  Verbindungs-/User-Agent-/Rate-Limit-Belange dahinter bündeln.
- **Erledigt:** Interface `StreamAvailabilityProvider.query(imdbId)` eingeführt,
  von `WerStreamtEsApiClient` implementiert;
  `StreamInfoService` und `QueryController` hängen jetzt am Interface (Test mockt das Interface).

### ✅ TODO-37 — `AggregateService.getAll()` liefert `List<List<QueryResult>>`
Verschachtelte Form, die Aufrufer sofort flachklopfen.
- **Akzeptanzkriterium:** Flaches `List<QueryResult>` bzw. `Map` (wie `resolveAll`) zurückgeben;
  `included`/`paid` als ein Filter mit Prädikat.
- **Erledigt:** `getAll()` liefert flaches `List<QueryResult>`;
  `included`/`paid` teilen das Prädikat `on(serviceName)` (kombiniert mit `flatrate` bzw. dessen Negation).

---

## Invalidierungs-Feature (2026-06-28)

### ✅ TODO-38 — Einträge gezielt invalidieren (UI)
Eintrage in der UI auswählen und deren Cache invalidieren (für bewusstes Neu-Scrapen).
- **Akzeptanzkriterium:** Auswahl in der UI → markierte Einträge werden invalidiert.
- **Erledigt:** `QueryMetaRepository.invalidateByImdbIds(...)` (`@Modifying`),
  `PreCacheService.invalidate(...)`, Web-Endpunkt `POST /invalidate` und die `/manage`-Seite
  (Checkbox-Auswahl).
  Integrationstest gegen H2 + Mockito-Tests ergänzt.

### ✅ TODO-39 — Nur invalidierte/fehlende Einträge scrapen (UI)
Eine UI, die gezielt nur die invalidierten (bzw. nie gecachten) Einträge scrapt.
- **Akzeptanzkriterium:** Button/Endpunkt scrapt nur die Einträge ohne gültigen Cache.
- **Erledigt:** `PreCacheService.cacheUncached()` (nutzt `findUncached()`),
  Endpunkt `POST /scrape-invalidated`, Button auf `/manage`;
  Navbar-Link „Manage Cache".

---

## Empfehlung zu TODO-23 (`ResponseEntity<?>`)

`rest/QueryController.query(...)`/`search(...)` geben `ResponseEntity<?>` zurück. Empfehlung:

- **Konkreter Typ + sprechende Fehler:** Beide Handler liefern logisch `List<QueryResult>`.
  Auf `ResponseEntity<List<QueryResult>>` umstellen und die „nicht gefunden"-Fälle nicht
  über einen rohen `ResponseEntity<?>`-Mischtyp, sondern über `ResponseEntity.notFound().build()`
  (gleicher generischer Typ) bzw. eine `ResponseStatusException(NOT_FOUND)` abbilden.
  Damit ist die Methode typsicher und Tests können `getBody()` ohne Cast nutzen.
- **Hilfsmethoden vereinheitlichen:** `searchById`/`searchByImdbId` geben bereits `ResponseEntity<List<QueryResult>>` zurück
  — `query()`/`search()` sollten denselben Typ führen statt `?`.
- **Optional (sauberer):** Statt `ResponseEntity` ganz auf den Body-Typ gehen (`List<QueryResult>`)
  und 404 über eine zentrale `@ControllerAdvice`/`ResponseStatusException` behandeln (greift in TODO-20 ein)
  — dann braucht der Controller gar kein `ResponseEntity` mehr.
- **Empfehlung:** Kurzfristig Variante 1 (konkreter `ResponseEntity<List<QueryResult>>`);
  mittelfristig zusammen mit TODO-20 (zentrales Error-Handling) auf reine Body-Rückgaben + `ResponseStatusException` umstellen.

---

## Bugfixes

### ✅ BUG — Provider mit mehreren Sprach-Listings wurde komplett verworfen
`WerStreamtEsApiClient` verarbeitete nur 3 oder 6 `.columns.small-4` pro Anbieter. Listet ein
Anbieter denselben Titel mehrfach (z. B. Prime Video „Priest" in 3 Sprachen → 9 Spalten), wurde
mit `Unexpected column count 9` der **ganze Anbieter** fallen gelassen.
- **Erledigt:** Parser arbeitet jetzt pro Listing-Zeile (`.panel.available`), liest die Sprache
  aus dem Titelblock und dedupliziert nach (Flatrate + Preise + Sprache). Mehrere distinkte
  Listings ergeben je einen Eintrag, per Sprache unterschieden (`label()` = „Prime Video (…)"),
  ein einzelnes Listing bleibt ohne Suffix. Neues Feld `QueryResult.languages` +
  Spalte `query_result.languages` (Liquibase `002`). `included()` dedupliziert nach `imdbId`.
  Integrationstest gegen eine bereinigte echte Detailseite (`priest-tt0822847.html`).

---

## DB / Portierbarkeit

### ✅ TODO-40 — Liquibase-Changesets von SQL auf XML
Die Changesets waren H2-spezifisches Roh-SQL (`uuid`, `enum('BUY','RENT')`,
`timestamp(6) with time zone`) und damit nicht portabel.
- **Akzeptanzkriterium:** Changesets als XML mit dialekt-portablen Change-Types; Schema läuft
  auf H2 **und** MariaDB.
- **Erledigt:** `001-baseline-schema.xml` / `002-add-query-result-languages.xml`
  (`createTable`/`addColumn`/`addForeignKeyConstraint`); dialektabhängige Typen via
  `${uuid.type}`/`${timestamp.type}`-Properties. `ddl-auto=none` (Liquibase ist alleinige
  Schema-Quelle; Korrektheit über die Repository-Tests auf H2 + MariaDB).

### ✅ TODO-41 — MariaDB als First-Class-DB + Testcontainers
- **Akzeptanzkriterium:** MariaDB als unterstützte DB; Repository-Test-Suite läuft gegen eine
  Testcontainers-MariaDB.
- **Erledigt:** MariaDB-Treiber, Profil `mariadb` (`application-mariadb.properties`),
  `mariadb`-Service in `compose.yml`. Repo-Tests in abstrakte Basen ausgelagert; je eine H2-
  und eine MariaDB-Variante (`@ServiceConnection MariaDBContainer`,
  `@Testcontainers(disabledWithoutDocker = true)` → ohne Container-Runtime übersprungen, nicht
  rot). H2 bleibt Default für Dev & In-Memory-Tests.

---

## Architektur-Enforcement (2026-07-20)

> **Update (2026-07-29):** Die hier beschriebene Schichtenarchitektur (Presentation → Application
> → Services → Persistence) ist durch die fachliche Gliederung nach Bounded Context ersetzt worden
> (`accountaccess`/`watchlist`/`titlecatalog`/`streamingavailability`, je mit eigenem
> `domain`/`application`/`port`/`adapter`-Baum) — siehe die neue ADR unter
> [`docs/adr`](docs/adr/README.md). `ArchitectureTest` erzwingt jetzt stattdessen die
> Context-Isolation (eine Regel pro Context) plus weiterhin die `now()`-Regel; die alte
> Schichtenregel wurde entfernt, da die "Services"-Schicht durch die Umstellung endgültig leer war.

Die Schichtenarchitektur (Presentation → Application → Services → Persistence, über dem
Domain-Leaf) und die „keine statischen `now()`-Aufrufe"-Regel ([ADR-0003](docs/adr/0003-zeit-ueber-timeservice-facade.md))
werden per **ArchUnit** erzwungen (`ArchitectureTest`); im Frontend prüft ESLint die
`now()`-Regel. Bekannte Verstöße sind als Ausnahmen eingetragen und hier zur Auflösung notiert.

### ✅ ARCH-1 — `CommonAttributeService` lag in der Services-Schicht, gehörte aber zur Präsentation
`CommonAttributeService` schreibt das `selectedList`-Attribut ins Thymeleaf-`Model` und wird nur
von den `web`-Controllern genutzt — lag aber im `services`-Paket, sodass die Präsentationsschicht
direkt auf die Services-Schicht zugriff (einziger Verstoß gegen „Presentation hängt nur von
Application (+ Domain) ab").
- **Erledigt:** Nach `tech.dobler.werstreamt.web` verschoben (jetzt `@Component` der
  Präsentationsschicht) und die Datenquelle von `ImdbCatalog` (Services) auf
  `ListSelectionService.currentList()` (Application) umgestellt — damit hängt kein Controller
  mehr an der Services-Schicht. Die `ignoreDependency`-Ausnahme in `ArchitectureTest` ist
  entfernt; die Schichtenregel greift jetzt ohne Ausnahme. (Historische Notiz: Klassen- und
  Paketnamen von damals sind seither mehrfach umgezogen, s.o.)
- **Hinweis:** Für den Angular-Client gibt es kein Äquivalent (die aktive Liste kommt dort über
  `GET /api/lists`), d. h. der Service ist rein Thymeleaf-spezifisch.

---

## Architektur-Review (2026-07-28)

Vollständige Analyse: [`docs/ARCHITECTURE_REVIEW.md`](docs/ARCHITECTURE_REVIEW.md). Die meisten
Funde wurden direkt umgesetzt (siehe Commit-Historie); ein Punkt wird hier stattdessen als
Low-Prio-Ticket für später vorgemerkt, statt sofort umgesetzt zu werden.

### 🟢 TODO-42 — Keine Mindestlänge/Komplexität für Passwörter
`UserAdminService.create()`/`resetPassword()` prüfen nur `requireText()` (nicht-leer), keine
Mindestlänge oder Komplexität — ein ADMIN kann einem Account ein Ein-Zeichen-Passwort geben.
Ebenso keine Prüfung für das initiale Admin-Passwort (`w2s.security.initial-admin.password`).
- **Akzeptanzkriterium:** Sinnvolle Mindestanforderungen (Länge, ggf. Zeichenklassen) einführen,
  serverseitig durchsetzen, Fehlermeldung im Frontend anzeigen.
- **Bewusst zurückgestellt:** Das bekannte Default-Passwort in `.env.example`
  (`W2S_ADMIN_PASSWORD=change-me-please`) ist kein eigenständiges Problem — der Platzhaltertext
  ist die etablierte "bitte ändern"-Konvention dieser Datei (vgl. `MARIADB_ROOT_PASSWORD=change-me`
  direkt darunter), und `compose.yml` übersetzt die vereinfachten `.env`-Variablennamen bereits
  korrekt auf die tatsächlichen Spring-Property-Namen (`W2S_SECURITY_INITIALADMIN_PASSWORD` etc.,
  mit erklärendem Kommentar zur Relaxed-Binding-Eigenheit). Nur die fehlende
  Längen-/Komplexitätsprüfung selbst bleibt offen.

## Async Cache-Refresh statt synchronem Dashboard-Reload (2026-07-30/31)

Vollständiger Plan: [`docs/CACHE_REFRESH_PLAN.md`](docs/CACHE_REFRESH_PLAN.md),
Entscheidung: [ADR-0016](docs/adr/0016-asynchrone-verzoegerte-cache-aktualisierung.md).
Auslöser: die „Cache Verwalten"-Seite (`/manage`) hatte keinen beobachtbaren Effekt, weil
`StreamInfoService.resolveAll(...)` (Dashboard/Provider-Seiten) invalidierte/abgelaufene Einträge
synchron im selben Request nachlud — ein einziger Dashboard-Aufruf hob jede manuelle
Invalidierung sofort wieder auf, bevor die Manage-Seite etwas zu tun hätte.

### ✅ TODO-43 — Manage-Tabelle: Zeitstempel statt reinem „gecacht"-Boolean
`ManageRowDto`/`ManageTable` zeigten nur `needsScrape` (ja/nein), keinen Zeitpunkt.
- **Akzeptanzkriterium:** Pro Titel wird der Zeitpunkt des letzten Scrapes angezeigt (oder „nie"),
  nicht mehr nur ein binärer Pill; ein invalidierter Titel zeigt weiterhin „muss gescrapt werden".
- **Erledigt:** `QueryMetaRepository.findByImdbIdIn(...)` (ohne Invalidiert-Filter) + `ManageRowDto.lastScrapedAt`;
  `manage-table.ts` zeigt bei `needsScrape=false` den formatierten Zeitpunkt (Angular `DatePipe`)
  statt der bisherigen „gecacht"-Pill (`manage.statusCached` entfernt).
  Details: `docs/CACHE_REFRESH_PLAN.md`, Phase 1.

### ✅ TODO-44 — `resolveAll` liefert veraltete Daten sofort + Refresh im Hintergrund
`StreamInfoService.resolveAll(...)` blockierte den Request auf jedem invalidierten/abgelaufenen
Treffer, statt die vorhandenen Werte sofort zu liefern und asynchron nachzuladen.
- **Akzeptanzkriterium:** Ein vorhandener, aber veralteter Cache-Eintrag wird sofort (mit
  `stale = true`) zurückgegeben; der Refresh läuft dedupliziert im Hintergrund (`@Async`). Nur ein
  nie gecachter Titel bleibt synchron. Neue Spalte `due_for_refresh_at` (Jitter, beim Schreiben
  gewürfelt) legt den Grundstein für TODO-46.
- **Erledigt:** `resolveAll` liefert `Map<ImdbId, ResolvedEntry>` (`results`, `stale`); ein
  vorhandener invalidierter/abgelaufener Eintrag wird sofort mit `stale=true` zurückgegeben und
  löst `StreamInfoService.refreshInBackground(imdbId)` (`@Async("cacheRefreshExecutor")`,
  aufgerufen über den bestehenden `self`-Proxy) an, dedupliziert über die neue
  `RefreshInFlightTracker`-Komponente (`shared/platform/concurrency`); ein nie gecachter Titel
  bleibt synchron. Liquibase `015-query-meta-due-for-refresh-at.xml` ergänzt `due_for_refresh_at`;
  `StreamInfoService.fetch(...)` würfelt ihn beim Schreiben (`wer-streamt.invalidate.jitter-min-factor`/
  `-max-factor`, Default 1.5/2.0). Neue `AsyncConfig` (`@EnableAsync`, `cacheRefreshExecutor`,
  Pool-Größe 2 — der bestehende `RateLimiter` drosselt ohnehin).
  Details: `docs/CACHE_REFRESH_PLAN.md`, Phase 2.

### ✅ TODO-45 — „Veraltet"-Banner auf Dashboard und Provider-Seiten
Es gab keine Kennzeichnung, wenn angezeigte Streaming-Verfügbarkeiten veraltet sind.
- **Akzeptanzkriterium:** Ein kleiner, seitenweiter Hinweis-Banner (kein Fehler) erscheint, wenn
  mind. ein angezeigter Titel `stale` ist (kein Per-Zeile-Flag, YAGNI).
- **Erledigt:** `CatalogPageDto` (`entries` + `hasStaleEntries`) und `ProviderPageDto.hasStaleEntries`;
  neue `StaleDataBanner`-Komponente (Vorlage `ErrorAlert`, eigenes Token
  `--mat-sys-secondary-container`) auf Dashboard und Provider-Seite eingebunden.
  Details: `docs/CACHE_REFRESH_PLAN.md`, Phase 3.

### ✅ TODO-46 — Scheduled Job für proaktives, gestaffeltes Nachladen
Titel, die niemand ansieht, veralten unbegrenzt, bis sie zufällig wieder aufgerufen werden.
- **Akzeptanzkriterium:** Ein täglicher (konfigurierbarer) Job aktualisiert nur fällige Titel
  (invalidiert, oder TTL × Jitter-Faktor 1,5–2,0 verstrichen) unter den aktuell gewatchlisteten
  Titeln — kein Effekt, wenn nichts fällig ist (keine unnötige Last bei Nichtnutzung).
- **Erledigt:** `BackgroundCacheRefreshService.refreshDueEntries()` (batch-lädt wie
  `CacheManagementService.managePage()` und reduziert auf die jeweils neueste `QueryMeta`-Zeile pro
  Titel, statt einer eigenen `@Query`) + `adapter/in/scheduled/CacheRefreshScheduler`
  (`@Scheduled(cron = "${wer-streamt.background-refresh.cron:0 0 4 * * *}")`,
  `wer-streamt.background-refresh.enabled` als Not-Aus). Teilt sich `RefreshInFlightTracker` und
  `StreamInfoService.refreshInBackground(...)` mit dem bedarfsgetriebenen Pfad aus TODO-44.
  Details: `docs/CACHE_REFRESH_PLAN.md`, Phase 4.

---

## Bug (2026-07-31)

### ✅ TODO-47 — TMDB-Posterdownload schlägt fehl, wenn `title_poster.poster_path` von der IMDb-Quelle stammt
Produktions-Log (`tmdb.enabled=true`):
```
WARN t.d.w.t.a.out.tmdb.TmdbPosterSource : TMDB FULL image download
  https://image.tmdb.org/t/p/w500https://m.media-amazon.com/images/M/MV5BMjIzNTA0OTIxNV5BMl5BanBnXkFtZTcwMzA3MTM2Nw@@._V1_.jpg
  returned HTTP 404 (1957 bytes)
```
`TmdbPosterSource.download(posterPath, size)` (`titlecatalog/adapter/out/tmdb/TmdbPosterSource.java`)
baut die Download-URL immer als `imageBaseUrl + "/" + tmdbSize(size) + posterPath` — es wird
angenommen, dass `posterPath` ein TMDB-relativer Pfad ist (z. B. `/abc123.jpg`).
`title_poster.poster_path` ist aber eine einzige, quellenunabhängige Spalte (`PosterService.classify`/`storePath`):
Wurde der Pfad ursprünglich von `ImdbPosterSource` ermittelt, ist er eine **volle** Amazon-CDN-URL
(`https://m.media-amazon.com/...`). Läuft die Instanz später (oder gleichzeitig, je nach Konfiguration)
mit `tmdb.enabled=true`, liest `PosterService.get(...)` diesen alten Pfad aus `title_poster` (Zeile hat
noch keine Bytes für die angefragte Größe → `Cached.needsDownload(row.getPosterPath())`) und reicht ihn
unverändert an `TmdbPosterSource.download(...)` durch — die beiden URLs werden ohne Trenner
zusammengeklebt, TMDB antwortet mit 404, der Poster bleibt dauerhaft leer für diesen Titel
(kein Retry-Mechanismus für „Pfad vorhanden, aber falsches Format").
- **Akzeptanzkriterium:** Ein `posterPath`, der nicht zur aktiven Quelle passt (z. B. beginnt er
  bereits mit `http`, obwohl TMDB aktiv ist), darf nicht blind an die Bild-CDN-URL angehängt werden.
  Entweder den Pfad pro Quelle kennzeichnen/trennen (z. B. eigene Spalte oder ein Präfix, das beim
  Quellenwechsel invalidiert), oder `TmdbPosterSource.download(...)` defensiv prüfen und bei einem
  bereits absoluten `posterPath` (nicht TMDB-Format) wie bei „kein Poster" behandeln (negativ cachen,
  damit `findPosterPath` erneut über TMDB auflöst statt denselben falschen Pfad endlos wiederzuverwenden).
- **Hinweis:** Betrifft vermutlich jede Instanz, die die Poster-Quelle nach dem ersten Befüllen von
  `title_poster` umgestellt hat (`imdb.enabled`/`tmdb.enabled` getauscht) — kein Einzelfall.
- **Erledigt:** Neue `PosterPort.isValidPosterPath(String)` (Default `true`), von `TmdbPosterSource`
  (`posterPath.startsWith("/")`) und `ImdbPosterSource` (`startsWith("http://"/"https://")`) jeweils
  auf ihr eigenes Pfad-Format eingeschränkt überschrieben. `PosterService.classify(...)` behandelt
  einen zur aktiven Quelle nicht passenden `posterPath` wie „noch nicht aufgelöst"
  (`Cached.needsDiscovery()`) statt ihn blind an `download(...)` durchzureichen — der nächste
  Zugriff löst über die aktuell aktive Quelle neu auf und überschreibt Pfad **und** alte Bytes
  (`TitlePoster.refresh(...)`, self-healing ohne manuellen Eingriff).

---

## Feature (2026-07-31)

### ✅ TODO-48 — Sortierbarkeit der „Cache Verwalten"-Oberfläche
Die Manage-Tabelle (`/manage`, `ManageTable`) hatte keine Sortierung — anders als die
Verfügbarkeits-Tabellen (Dashboard/Provider-Seiten), die bereits per Klick auf die Spaltenüberschrift
nach Titel/Jahr/hinzugefügt sortierbar sind (`shared/sort/table-sort.ts`, `MatSortModule`).
- **Akzeptanzkriterium:** Die Manage-Tabelle lässt sich per Klick auf die Spaltenüberschrift nach
  **Name** und nach **Datum** (Zeitpunkt des letzten Scrapes, `lastScrapedAt` aus TODO-43) sortieren,
  auf- und absteigend, nach demselben Muster (`mat-sort-header`) wie die bestehenden Tabellen.
- **Erledigt:** Neue `sortManageRows(...)` in `shared/sort/table-sort.ts` (eigene kleine Funktion
  statt Erweiterung von `sortRows`, da die Manage-Tabelle weder `year` noch `added` hat); nie
  gescrapte Titel (`lastScrapedAt = null`) sortieren aufsteigend ans Ende / absteigend an den
  Anfang, analog zum bestehenden `year`-Sonderfall „Not yet released". `ManageTable` verdrahtet
  `MatSortModule`/`matSort` wie `CatalogTable`; die Status-Spalte trägt `mat-sort-header="lastScrapedAt"`
  (abweichend vom `matColumnDef`-Namen `status`), da sie sowohl die „muss gescrapt werden"-Pille als
  auch den Zeitstempel zeigt.

---

### ✅ F12 — Controller umgehen `ApiExceptionHandler` via rohem `ResponseStatusException`
`MeApiController` (6×), `WatchlistApiController` (2×) und `ImdbSearchApiController` (1×) warfen
`ResponseStatusException` direkt statt einer gemappten Exception, wodurch die Fehlermeldung ohne
`spring.mvc.problemdetails.enabled`/`server.error.include-message` verloren gehen konnte.
- **Erledigt:** Neue `application.ValidationException` (trägt optional einen `HttpStatus`, Default
  `BAD_REQUEST`, analog zu `UserManagementException`) ersetzt alle 9 Stellen. `ApiExceptionHandler`
  bildet sie auf eine `ProblemDetail` mit dem jeweiligen Status ab. Live verifiziert: fehlendes
  `theme`-Feld → `400` mit `{"detail":"A theme is required.", "title":"Invalid request", ...}` statt
  einer leeren Standard-Fehlerseite; `tilesPerRow`-Bereichsprüfung ebenso. Die beiden
  `ResponseStatusException`-404-Fälle (`SearchApiController`, `ProviderApiController`, "unbekannte
  Ressource" statt Validierung) wurden bewusst nicht angefasst — andere Fehlerkategorie, außerhalb
  von F12s "400-Validierung"-Fokus.
