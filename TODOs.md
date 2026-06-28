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

TODO-Tickets:

- ✅ **TODO-2** — Tabellen-/Spalten-Tippfehler (`QueryMeta`, `query_result_availabilities`).
- ✅ **TODO-3** — Irreführende Join-Spalte → `query_result_id`.
- ✅ **TODO-10 / TODO-27** — Liquibase eingeführt, Schema als versioniertes Changelog,
  `ddl-auto=validate`.
- ✅ **TODO-6** — Cache-Logik in `PreCacheService` extrahiert; Controller hängen nicht
  mehr voneinander ab.
- ✅ **TODO-7** — `wer-streamt.*` in `WerStreamtProperties` (`@ConfigurationProperties`)
  gebündelt; `@Value`-Field-Injection entfernt.
- ✅ **TODO-8** — `ImdbEntryRepository` nutzt einen unveränderlichen Snapshot hinter
  `AtomicReference` (lock-freie Reads, atomarer Reload).
- ✅ **TODO-9** — Robustes Scraping: Null-Guards + try/catch pro Provider, Spalten-Anzahl
  vor Zugriff geprüft; aktueller User-Agent.
- ✅ **TODO-18** — Fehlende Qualitäten sind jetzt `null`-`Price` statt `new Price(null)`;
  `prettyPrint`-Null-Checks greifen dadurch korrekt.
- ✅ **TODO-17** — Aufräumarbeiten: ungenutzter `@Value`-Import (in TODO-7), parametrisiertes
  Logging (`WerStreamtEsApiClient` in TODO-9, `ImdbApiClient`), `@GetMapping("/public/status")`.

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

### ✅ TODO-2 — Tabellen-/Spalten-Tippfehler
- `persistence/QueryMeta.java`: `@Table(name = "QeryMeta")` → `QueryMeta`.
- `persistence/QueryResultDB.java`: `query_result_availablilities` → `query_result_availabilities`.
- **Achtung:** Schema-Migration nötig (siehe TODO-10), `ddl-auto=update` benennt
  Tabellen nicht automatisch um → bestehende Daten gehen sonst verloren.
- **Erledigt:** Entity-Annotationen korrigiert; das korrigierte Schema steckt im
  Liquibase-Baseline (TODO-27).

### ✅ TODO-3 — Irreführender Join-Spaltenname
`persistence/QueryResultDB.java`: `@CollectionTable(joinColumns = @JoinColumn(name = "imdb_id"))`
joint tatsächlich auf die UUID-PK von `QueryResultDB`, nicht auf eine IMDb-ID.
- **Akzeptanzkriterium:** Spalte z. B. in `query_result_id` umbenennen (mit Migration).
- **Erledigt:** Join-Spalte heißt jetzt `query_result_id`; Schema via Liquibase (TODO-27).

---

## Sicherheit

### 🔴 TODO-5 — Zustandsändernde Endpunkte als GET ohne Auth
`/pre-cache`, `/check-pre-cache`, `/refresh/all`, `/refresh/seen` lösen teure
Remote-Crawls aus, sind per GET erreichbar und damit von Crawlern/Prefetch triggerbar.
- **Akzeptanzkriterium:** Auf `POST` umstellen; Endpunkte hinter Authentifizierung
  legen (Spring Security ergänzen — die App ist aktuell komplett offen).

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
`services/WerStreamtEsApiClient.java`: `selectFirst(...).childNode(0)` u. ä. ohne
Null-Checks; eine Layout-Änderung bei werstreamt.es konnte NPEs auslösen. (Review-Punkt #4,
Korrektheit; wurde aber bewusst zurückgestellt.)
- **Akzeptanzkriterium:** Null-Guards + try/catch pro Eintrag, damit ein fehlerhafter
  Eintrag nicht den ganzen Lauf abbricht. Veralteter User-Agent (Firefox 2.0.0.6, 2007)
  aktualisieren.
- **Erledigt:** `parseProvider` kapselt jeden Provider in try/catch und prüft die
  Spalten-Anzahl vor dem Indexzugriff; `qualityLabel`/`priceText` und `toSearchResult`
  sind null-sicher; User-Agent auf aktuellen Chrome aktualisiert. Tests
  `skipsProviderWithUnexpectedColumnCount` / `skipsMalformedEmWithoutCrashing` ergänzt.

### ✅ TODO-10 — Schema-Versionierung statt `ddl-auto=update`
`application.properties`: `spring.jpa.hibernate.ddl-auto=update`.
- **Akzeptanzkriterium:** Flyway oder Liquibase einführen für reproduzierbare,
  versionierte Schemata (Voraussetzung für TODO-2 und TODO-3).
- **Erledigt:** Über TODO-27 (Liquibase) umgesetzt; `ddl-auto=validate`.

---

## Performance

### ✅ TODO-11 — Mehrfache Voll-Auflösung pro Seitenaufruf
`services/AggregateService.java`: `getAll()` löst alle Einträge sequenziell auf.
Die Amazon-Seite (`web/DataAggregateController.getAmazon`) rief `included()` **und**
`paid()` auf → `getAll()` lief **zweimal** pro Request.
- **Akzeptanzkriterium:** `getAll()` einmal aufrufen und beide Filter auf das Ergebnis
  anwenden.
- **Erledigt:** `AggregateService.contentFor(serviceName)` löst einmal auf und liefert
  `included` + `paid` (Record `ServiceContent`); die Amazon-Seite nutzt das.

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

### ✅ TODO-17 — Aufräumarbeiten
- `configurations/JpaConfig.java`: ungenutzter Import `org.springframework.beans.factory.annotation.Value`.
  → in TODO-7 entfernt (Klasse umgebaut).
- `services/WerStreamtEsApiClient.java` (`search`) und `services/ImdbApiClient.java` (`search`):
  String-Konkatenation im Logging (`"Searching for: " + ...`) → parametrisiertes Logging.
  → erledigt (WerStreamtEsApiClient in TODO-9, ImdbApiClient hier).
- `web/StatusController.java`: `@GetMapping("public/status")` ohne führenden Slash
  (inkonsistent zu den übrigen Mappings). → erledigt: `@GetMapping("/public/status")`.

### ✅ TODO-18 — `Price` wrappt fehlende Werte statt `null`
`services/WerStreamtEsApiClient.parseAvailability(...)`: fehlende Qualitäten werden als
`new Price(null)` gespeichert, d. h. `availability.sd()` etc. ist nie `null`, sondern ein
Price-Objekt mit `value() == null`. Aufrufer (z. B. `DataAggregateController.prettyPrint`)
prüfen aber auf `a.fourK() != null` — das ist dadurch immer wahr und `value()` kann `null`
ausgegeben werden.
- **Akzeptanzkriterium:** Fehlende Preise konsistent als `null`-`Price` (Optional/echtes
  `null`) modellieren und die Aufrufer entsprechend anpassen. (Beim Code-Review-Test
  #15 aufgefallen.)
- **Erledigt:** `priceOrNull(...)` liefert `null` für nicht angebotene Qualitäten;
  `prettyPrint` (das bereits auf `!= null` prüft) gibt damit keine `null`-Werte mehr aus.

---

## Aus dem Re-Scan (2026-06-27, nach Umsetzung von TODO-6/7/8/9/17/18)

### 🟠 TODO-19 — `/query` umgeht den Cache
`rest/QueryController.query(...)` ruft `werStreamtEsApiClient.query(...)` **direkt** auf und
scrapet damit bei jedem Aufruf live, während `/search` über `StreamInfoService` (gecacht)
geht. Inkonsistent und teuer.
- **Akzeptanzkriterium:** `/query` ebenfalls über `StreamInfoService.resolve(...)` laufen
  lassen (oder den Endpunkt entfernen, falls redundant zu `/search`).

### 🟠 TODO-20 — Kein zentrales Fehler-Handling
Scraping-/IO-Fehler werden in `WerStreamtEsApiClient`/`ImdbApiClient` als nacktes
`new RuntimeException(e)` weitergeworfen und landen ungefiltert als HTTP 500. Es gibt keinen
`@ControllerAdvice`/`@ExceptionHandler`.
- **Akzeptanzkriterium:** Zentrales Exception-Handling ergänzen; IO-Fehler in eine
  domänenspezifische Exception kapseln und sauber als 502/503 o. ä. abbilden.

### ✅ TODO-21 — `ExportReader` bricht beim ganzen Import ab, wenn eine Zeile fehlerhaft ist
`services/ExportReader.parse(...)`: `Integer.parseInt(year)` (NumberFormatException) bzw.
`extractImdbId(url)` (IllegalArgumentException) waren nicht pro Zeile abgesichert — eine
einzige kaputte Zeile ließ den gesamten Import (und damit den App-Start) scheitern.
- **Akzeptanzkriterium:** Pro Zeile try/catch, fehlerhafte Zeilen loggen und überspringen
  (analog zur Provider-Robustheit aus TODO-9).
- **Erledigt:** Zeilen-Parsing in `toEntry(...)` extrahiert; der Loop fängt
  `RuntimeException` pro Zeile, loggt die Zeilennummer und überspringt. Der id-Zähler
  läuft nur bei Erfolg weiter (kontinuierliche ids). Test
  `skipsMalformedRowsAndKeepsIdsContiguous` ergänzt.

### 🟢 TODO-22 — Hartkodiertes CSV-Header-Array
`services/ExportReader.headers`: feste Spaltenliste; bricht still, wenn IMDb das
Exportformat ändert.
- **Akzeptanzkriterium:** Header aus der Datei lesen
  (`CSVFormat.builder().setHeader().setSkipHeaderRecord(true)`) und nur die benötigten
  Spalten gezielt referenzieren.

### 🟢 TODO-23 — `ResponseEntity<?>` mit rohem Wildcard
`rest/QueryController`: `query(...)` und `search(...)` geben `ResponseEntity<?>` zurück —
keine Typsicherheit für die Aufrufer/Tests.
- **Akzeptanzkriterium:** Konkrete Rückgabetypen (`ResponseEntity<List<QueryResult>>` o. ä.).

### ✅ TODO-24 — Tests für neue/ungetestete Service-Logik fehlen
Nach den Refactorings waren `PreCacheService`, `StreamInfoService.resolveAll(...)`
(Caching/Threshold/Batch-Miss-Fetch) und das atomare Reload-Verhalten von
`ImdbEntryRepository` nicht durch Unit-Tests abgedeckt.
- **Akzeptanzkriterium:** Gezielte Unit-Tests ergänzen (Mockito für die Repos/Clients).
- **Erledigt:** `ImdbEntryRepositoryTest`, `PreCacheServiceTest`, `StreamInfoServiceTest`
  (Cache-Hit/Miss/Expiry/forceRefresh/Batch). Beim Schreiben fiel TODO-28 auf.

### ✅ TODO-25 — Aggregat-Seiten berechnen bei jedem Request alles neu
`web/DataAggregateController` + `services/AggregateService`: jede Anbieter-Seite ruft
`getAll()` auf und löste damit sämtliche Einträge sequenziell auf (über TODO-11 hinaus, das
nur den doppelten `getAll()`-Aufruf der Amazon-Seite betrifft).
- **Akzeptanzkriterium:** Aggregat-Ergebnisse cachen/vorberechnen bzw. die Batch-Logik aus
  `resolveAll(...)` (TODO-13/#13) wiederverwenden.
- **Erledigt:** `getAll()` nutzt jetzt `streamInfoService.resolveAll(...)` → eine
  Batch-Query statt N Einzelabfragen. (Echtes Aggregat-Caching bleibt als optionale
  spätere Optimierung offen.)

---

## Aus dem Re-Scan (2026-06-28)

### ✅ TODO-26 — Fehler-Logs ohne Query-Kontext
`services/WerStreamtEsApiClient`: Die `catch`-Blöcke in `query(...)` und `search(...)`
loggten `log.error("Not found %s".formatted(e.getMessage()))` bzw. warfen
`new RuntimeException(e)`, ohne anzugeben, **für welche Query/imdbId** der Fehler auftrat.
Bei den `parallelStream`-Läufen (Pre-Cache/Refresh) war so nicht nachvollziehbar, welcher
Eintrag fehlschlug.
- **Akzeptanzkriterium:** In allen Fehlerausgaben des Clients die betroffene Query
  (imdbId bzw. Suchbegriff) mitloggen.
- **Erledigt:** `query`/`search` loggen bzw. wrappen Fehler jetzt mit imdbId/Suchbegriff.

### ✅ TODO-29 — Requests/Sekunde gegen werstreamt.es begrenzen
Pre-Cache/Refresh feuern via `parallelStream` viele Requests gleichzeitig gegen
werstreamt.es — unhöflich und ein Block-Risiko.
- **Akzeptanzkriterium:** Outbound-Requests drosseln; konfigurierbares, sinnvoll
  vorbelegtes Property.
- **Erledigt:** `RateLimiter` (global, `synchronized`, mindest-Abstand zwischen Requests),
  `WerStreamtEsApiClient.query/search` rufen `acquire()` vor dem HTTP-Get; Property
  `wer-streamt.rate-limit.requests-per-second` (Default `2`, `<= 0` deaktiviert).

### ✅ TODO-28 — `forceRefresh` war invertiert (Refresh refetchte nie)
`services/StreamInfoService.resolve(imdbId, forceRefresh)`: Der Filter lautete
`forceRefresh || isFresh(...)`. Bei `forceRefresh == true` wurde der gecachte Eintrag dadurch
**behalten** statt neu geholt — d. h. die `/refresh/*`-Endpunkte (die `resolve(id, true)`
aufrufen) aktualisierten die Daten nie.
- **Akzeptanzkriterium:** `forceRefresh == true` erzwingt einen erneuten Fetch.
- **Erledigt:** Bedingung zu `!forceRefresh && isFresh(...)` korrigiert; beim Schreiben der
  Tests (TODO-24) aufgefallen. Test `resolveForceRefreshAlwaysFetches` deckt es ab.

### ✅ TODO-27 — Liquibase einführen und DB-Schema als Changelog ablegen
Das Schema wurde von Hibernate per `ddl-auto=update` verwaltet (siehe auch TODO-10).
- **Akzeptanzkriterium:** Liquibase einbinden, das vollständige Schema als Changelog
  hinterlegen und `ddl-auto` auf `validate` umstellen, sodass das Schema reproduzierbar und
  versioniert ist. Dies ist auch die Voraussetzung für die Umbenennungen aus TODO-2/TODO-3.
- **Hinweis:** Die H2-DB hält ausschließlich gecachte Scrape-Ergebnisse; das Baseline-Schema
  geht von einer frischen DB aus (für bestehende Deployments altes `./db` entfernen — der
  Cache füllt sich via `/pre-cache` neu).
- **Erledigt:** `spring-boot-liquibase` ergänzt; Baseline-Changelog unter
  `src/main/resources/db/changelog/` (`db.changelog-master.yaml` → `changes/001-baseline-schema.sql`),
  generiert aus dem Hibernate-Schema (inkl. der TODO-2/TODO-3-Namen); `ddl-auto=validate`
  in Haupt- und Test-Konfiguration. Tests laufen grün gegen das von Liquibase erzeugte Schema.

---

## Architektur-Review (2026-06-28)

Vollständige Analyse: [`docs/ARCHITECTURE_REVIEW.md`](docs/ARCHITECTURE_REVIEW.md).
Die konkreten, umsetzbaren Punkte daraus:

### ✅ TODO-30 — `entities/` ist irreführend benannt
Das Paket `entities/` enthielt reine Domänen-Records (keine JPA-Entities); die echten
`@Entity`-Klassen liegen in `persistence/`.
- **Akzeptanzkriterium:** `entities/` → `domain/` umbenennen (ggf. `domainvalues/`
  hineinziehen); JPA-Entities bleiben in `persistence/`.
- **Erledigt:** `entities/` und `domainvalues/` zu `domain/` zusammengeführt
  (`ImdbEntry`, `QueryResult`, `SearchResult` + die `@Embeddable`-Werte `Availability`,
  `Price`, Enum `AvailabilityType`). JPA-`@Entity`-Klassen bleiben in `persistence/`.

### ✅ TODO-31 — `ImdbEntryRepository` ist kein Repository
Stateful In-Memory-Katalog, benannt wie ein Spring-Data-Repository und in `services/`.
- **Akzeptanzkriterium:** In `ImdbCatalog`/`WatchlistStore` umbenennen, klar von den
  Spring-Data-Repos in `persistence/` abgrenzen.
- **Erledigt:** Klasse → `ImdbCatalog` (Felder/Variablen/Bean-Methode `imdbCatalog`).

### ✅ TODO-32 — Nahezu identische Provider-Handler in `DataAggregateController`
`getDisney`/`getNetflix`/`getWow` (und `getAmazon`/`getGoogle`) unterschieden sich nur durch
Service-/View-Namen.
- **Akzeptanzkriterium:** Datengetrieben zusammenfassen (Enum/Map aus Pfad → Service+View),
  ~4 Methoden auf eine reduzieren.
- **Erledigt:** Gemeinsame Helfer `flatratePage(...)`, `sortedByAdded(...)`, `paidDtos(...)`;
  die Handler delegieren nur noch. (Explizite Routen beibehalten statt Catch-all-`{path}`,
  um Routing-Mehrdeutigkeit zu vermeiden.)

### 🟢 TODO-33 — Transaktionsgrenze auf einem Controller
`DataAggregateController` ist `@Transactional(readOnly = true)` auf Klassenebene.
- **Akzeptanzkriterium:** Transaktionsgrenzen in die Service-Schicht verschieben; Controller
  nicht transaktional.

### 🟢 TODO-34 — View-Model-Aufbau im Controller
`IndexDto`, `PaidDto` und `prettyPrint(...)` stecken im `DataAggregateController`.
- **Akzeptanzkriterium:** In einen Assembler/Formatter (oder DTO-Factory-Methoden)
  auslagern; Controller ruft nur noch den Assembler.

### 🟢 TODO-35 — `invalidated`-Flag ist faktisch tot
`QueryMeta.invalidated` wird nie auf `true` gesetzt, aber überall mitgefiltert.
- **Akzeptanzkriterium:** Invalidierung tatsächlich umsetzen (z. B. beim Refresh alte Zeilen
  invalidieren) **oder** Flag + Query-Suffix entfernen.

### ✅ TODO-36 — Provider-Abstraktion fürs Scraping
Kein Interface über „Stream-Verfügbarkeits-Provider"; fest an jsoup/werstreamt.es gekoppelt
(`ImdbApiClient` ist tot, siehe TODO-1).
- **Akzeptanzkriterium:** Interface `StreamAvailabilityProvider` (z. B.
  `List<QueryResult> query(String imdbId)`), implementiert von `WerStreamtEsApiClient`;
  Verbindungs-/User-Agent-/Rate-Limit-Belange dahinter bündeln.
- **Erledigt:** Interface `StreamAvailabilityProvider.query(imdbId)` eingeführt,
  von `WerStreamtEsApiClient` implementiert; `StreamInfoService` und `QueryController`
  hängen jetzt am Interface (Test mockt das Interface).

### ✅ TODO-37 — `AggregateService.getAll()` liefert `List<List<QueryResult>>`
Verschachtelte Form, die Aufrufer sofort flachklopfen.
- **Akzeptanzkriterium:** Flaches `List<QueryResult>` bzw. `Map` (wie `resolveAll`)
  zurückgeben; `included`/`paid` als ein Filter mit Prädikat.
- **Erledigt:** `getAll()` liefert flaches `List<QueryResult>`; `included`/`paid` teilen das
  Prädikat `on(serviceName)` (kombiniert mit `flatrate` bzw. dessen Negation).
