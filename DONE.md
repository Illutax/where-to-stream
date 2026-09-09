# DONE — erledigte und verworfene TODOs

> **Historisch. Wird nicht gepflegt.**
>
> Klassen-, Paket- und Pfadnamen sind der Stand **zum Zeitpunkt der Erledigung**.
> Der Umbau nach Bounded Contexts ([ADR-0014](docs/adr/0014-backend-nach-bounded-contexts-und-ports-adaptern.md))
> hat `services/`, `persistence/`, `web/`, `rest/` und `entities/` als Top-Level-Pakete abgelöst;
> viele Einträge hier zeigen noch dorthin. Das ist **kein Fehler, sondern erwartet** —
> ein Ticket beschreibt die Welt, in der es geschrieben wurde.
>
> Für den heutigen Aufbau: [`CLAUDE.md`](CLAUDE.md) und [`docs/adr/`](docs/adr/).
> Für offene Arbeit: [`TODOs.md`](TODOs.md).
>
> Deshalb läuft `DocumentationConsistencyTest` über `TODOs.md`, aber **nicht** über diese Datei.

Status: ✅ erledigt · ❌ verworfen

---

## Bugs / Korrektheit

---

### ✅ TODO-1 — `ImdbApiClient.search()` ist kaputt / ungenutzt
`services/ImdbApiClient.java`: lädt das Dokument mit `connect.get()`
und gibt dann hart `return null;` zurück.
Die Klasse ist außerdem kein Spring-Bean (kein `@Service`) und wird nirgends produktiv verwendet.
- **Akzeptanzkriterium:** Entweder das Parsen der IMDb-Listenseite korrekt implementieren
  (Rückgabe `List<SearchResult>` statt `null`) **oder** Klasse + zugehörigen Test löschen.
- **Anmerkung:** Der bestehende `ImdbApiClientTest` macht einen echten Netzwerk-Aufruf gegen imdb.com ohne Assertion
  und schlägt in der Sandbox am Egress-Proxy (403) fehl.
  Beim Aufräumen mitnehmen (durch einen Test gegen ein gespeichertes HTML-Fixture ersetzen).

---

### ✅ TODO-2 — Tabellen-/Spalten-Tippfehler
- `persistence/QueryMeta.java`: `@Table(name = "QeryMeta")` → `QueryMeta`.
- `persistence/QueryResultDB.java`: `query_result_availablilities` → `query_result_availabilities`.
- **Achtung:** Schema-Migration nötig (siehe TODO-10), `ddl-auto=update` benennt Tabellen nicht automatisch um
  → bestehende Daten gehen sonst verloren.
- **Erledigt:** Entity-Annotationen korrigiert;
  das korrigierte Schema steckt im Liquibase-Baseline (TODO-27).

---

### ✅ TODO-3 — Irreführender Join-Spaltenname
`persistence/QueryResultDB.java`: `@CollectionTable(joinColumns = @JoinColumn(name = "imdb_id"))`
joint tatsächlich auf die UUID-PK von `QueryResultDB`, nicht auf eine IMDb-ID.
- **Akzeptanzkriterium:** Spalte z. B. in `query_result_id` umbenennen (mit Migration).
- **Erledigt:** Join-Spalte heißt jetzt `query_result_id`; Schema via Liquibase (TODO-27).

---

---

## Sicherheit

---

### ✅ TODO-5 — Zustandsändernde Endpunkte als GET ohne Auth
`/pre-cache`, `/check-pre-cache`, `/refresh/all`, `/refresh/seen` lösten teure Remote-Crawls aus,
waren per GET erreichbar und damit von Crawlern/Prefetch triggerbar.
- **Akzeptanzkriterium:** Auf `POST` umstellen;
  Endpunkte hinter Authentifizierung legen (Spring Security ergänzen — die App war komplett offen).
- **Erledigt (Auth):** Spring Security ergänzt ([ADR-0006](docs/adr/0006-authentifizierung-und-autorisierung.md));
  die neue REST-API nutzt korrekte Verben (`POST /api/refresh`, `POST /api/cache`, …).
- **Erledigt (Verben):** Mit dem Entfernen des Thymeleaf-Clients
  ([ADR-0008](docs/adr/0008-thymeleaf-client-entfernen.md)) wurden die Legacy-GET-Endpunkte
  (`/pre-cache`, `/check-pre-cache`, `/refresh/**`) gelöscht — es gibt keine mutierenden GETs **ohne Auth** mehr (schreibende Read-Through-Caches wie `/api/search` und `/api/titles/{id}/meta` gibt es weiterhin — sie hängen hinter `authenticated()`);
  Wartung läuft ausschließlich über `POST /api/**` (ADMIN).

---

---

## Architektur / Design

---

### ✅ TODO-6 — Controller ruft Controller
`web/ChangeListController.java` injizierte `rest/PreCacheController` und rief
`cacheController.cache()`.
- **Akzeptanzkriterium:** Cache-Logik in einen `PreCacheService` extrahieren, den
  beide Controller verwenden.
- **Erledigt:** `PreCacheService.cacheAll()` / `findUncached()` eingeführt;
  `PreCacheController` und `ChangeListController` nutzen den Service.

---

### ✅ TODO-7 — Verstreute Konfiguration per `@Value`
`wer-streamt.path` wurde in `ExportReader` und `FileUtils` separat injiziert,
`wer-streamt.invalidate.after-days` in `StreamInfoService`.
- **Akzeptanzkriterium:** In ein `@ConfigurationProperties`-Record `WerStreamtProperties`
  bündeln. Damit entfällt auch die fragile `@Value`-Field-Injection in `FileUtils`
  (wird in `JpaConfig` per `new FileUtils()` erzeugt).
- **Erledigt:** `WerStreamtProperties` (mit `Invalidate.afterDays`, Default 28) per
  `@ConfigurationPropertiesScan` aktiviert. `FileUtils` ist jetzt `@Component` mit
  Konstruktor-Injection; `JpaConfig` injiziert es, statt `new FileUtils()` zu bauen.

---

### ✅ TODO-8 — `ImdbEntryRepository` ist nicht thread-safe
`services/ImdbEntryRepository.java`: In-Memory-Store auf `HashMap`, wurde aber während
laufender `parallelStream`-Requests via `clear()`/`init()` aus `ChangeListController`
neu befüllt → Race-Potenzial.
- **Akzeptanzkriterium:** `ConcurrentHashMap` + atomarer Austausch der Maps beim Reload,
  oder Reload synchronisieren.
- **Erledigt:** Gesamter Zustand (beide Maps + Listenname) als unveränderliches `State`-Record
  hinter einer `AtomicReference`; `init`/`clear` tauschen den Snapshot atomar, Reads sind
  lock-frei und konsistent.

---

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

---

### ✅ TODO-10 — Schema-Versionierung statt `ddl-auto=update`
`application.properties`: `spring.jpa.hibernate.ddl-auto=update`.
- **Akzeptanzkriterium:** Flyway oder Liquibase einführen für reproduzierbare,
  versionierte Schemata (Voraussetzung für TODO-2 und TODO-3).
- **Erledigt:** Über TODO-27 (Liquibase) umgesetzt; heute `ddl-auto=none`
  (`validate` war ein Zwischenstand, seit TODO-40 ist Liquibase alleinige Schema-Quelle).

---

---

## Performance

---

### ✅ TODO-11 — Mehrfache Voll-Auflösung pro Seitenaufruf
`services/AggregateService.java`: `getAll()` löst alle Einträge sequenziell auf.
Die Amazon-Seite (`web/DataAggregateController.getAmazon`) rief `included()` **und** `paid()` auf
→ `getAll()` lief **zweimal** pro Request.
- **Akzeptanzkriterium:** `getAll()` einmal aufrufen und beide Filter auf das Ergebnis anwenden.
- **Erledigt:** `AggregateService.contentFor(serviceName)` löst einmal auf und liefert `included` + `paid`
  (Record `ServiceContent`); die Amazon-Seite nutzt das.

---

### ❌ TODO-12 — Durchgängiges `FetchType.EAGER` *(verworfen — durch ADR-0011 überholt)*
`streamingavailability/domain/QueryMeta.java` (`@OneToMany`) und `QueryResultDB.java`
(`@ElementCollection`) laden alles eager, ebenso `accountaccess/domain/AppUser.roles`.

- ~~**Akzeptanzkriterium:** Auf LAZY umstellen~~ — **verworfen.**
  [ADR-0011](docs/adr/0011-kein-open-session-in-view.md) (Accepted) macht EAGER zur bewussten
  Regel: ohne Open-Session-in-View muss alles geladen sein, bevor die Transaktion endet.
  Eine pauschale Umstellung auf LAZY würde die ADR brechen, nicht erfüllen.
- **Das ursprüngliche Risiko ist unabhängig davon entschärft:**
  `spring.jpa.properties.hibernate.default_batch_fetch_size=50` (Commit `017bd35`,
  gemessen: 20 Titel × 2 Provider = 3 Statements statt N+1),
  und der fehlende FK-Index kam mit Changeset `014`.
- **Der vereinbarte Weg**, falls eine Collection doch einmal zu groß wird,
  ist ein gezielter Fetch-Join — nicht LAZY und erst recht nicht OSIV.

---

---

## Build / Betrieb

---

### ✅ TODO-13 — Cron zieht Pre-Release-Spring-Boot — **bewusst so belassen**
Git-History: `4.1.0-M1 → M2 → M3 → M4 → RC1 → 4.1.0`.
`upgrade-spring-boot.sh` nutzt `versions:update-parent` ohne `-DallowSnapshots=false` und ohne Milestone-Filter,
d. h. der Cron zieht automatisch Milestones/RCs.
- **Entscheidung des Auftraggebers (2026-09-06): so gewollt.** Milestones mitzunehmen ist hier kein
  Versehen, sondern der Zweck — das Projekt sammelt darüber früh Erkenntnisse über kommende
  Spring-Boot-Versionen, und ein privates Projekt mit fünf Nutzern kann sich das leisten.
- **Was diese Entscheidung tragfähig macht**, ist die Absicherung drumherum, nicht die Stabilität
  des Parents: der Prüflauf baut mit Tests durch die `verify`-Stufe, und ein Fehlschlag setzt seit
  TODO-55 den Arbeitsbaum sauber zurück, statt die Update-Kette zu verklemmen. Ohne diese beiden
  wäre ein instabiler Parent teuer geworden.

---

### ✅ TODO-14 — `versions-maven-plugin` ohne Version *(faktisch gepinnt — Prämisse war falsch)*
`pom.xml` deklariert das Plugin ohne eigenes `<version>` — die Annahme „also nicht
reproduzierbar" trifft aber nicht zu.

Die Version kommt aus dem `pluginManagement` von `spring-boot-dependencies`, aktuell **2.21.0**;
nachgeprüft am effektiven POM und an der tatsächlichen Auflösung
(`mvn -B versions:help` protokolliert `--- versions:2.21.0:help ---`).
Es wird also kein `LATEST` aus den Metadaten gezogen — genau dafür ist der leere Plugin-Block da.
Pro Commit ist der Build damit reproduzierbar, weil die Parent-Version im POM festliegt.

- **Was bleibt, ist eine schwächere Aussage:** die Plugin-Version wandert mit dem
  Spring-Boot-Parent mit, und der wird nächtlich aktualisiert, bewusst inklusive Milestones
  (TODO-13). Ein explizites `<version>` würde `upgrade-spring-boot.sh` von genau dieser
  Kopplung lösen — das ist ein Abwägungspunkt, kein Mangel.

---

### ✅ TODO-15 — Port-Inkonsistenz dokumentieren/vereinheitlichen
`server.port=8001` (properties), `EXPOSE 8080` (Dockerfile), `SERVER_PORT=8080` (compose).
Funktioniert, weil compose überschreibt.
- **Akzeptanzkriterium:** Werte angleichen oder in der README erklären.
- **Erledigt:** Werte bleiben bewusst unterschiedlich (Compose überschreibt),
  sind aber jetzt in der README-Konfigurationstabelle dokumentiert (`server.port` → „HTTP port (Docker overrides to 8080)").

---

### ✅ TODO-16 — README fehlt
Kein Setup-Dokument vorhanden.
- **Akzeptanzkriterium:** README mit Setup (CSV in `assets/` ablegen, Profile, Port,
  verfügbare Endpunkte) ergänzen.
- **Erledigt:** Umfassende README (Setup, Prerequisites, Profile inkl. `mariadb`/`google`,
  Konfigurationstabelle, vollständige `## Endpoints`-Übersicht).

---

---

## Kleinigkeiten

---

### ✅ TODO-17 — Aufräumarbeiten
- `configurations/JpaConfig.java`: ungenutzter Import `org.springframework.beans.factory.annotation.Value`.
  → in TODO-7 entfernt (Klasse umgebaut).
- `services/WerStreamtEsApiClient.java` (`search`) und `services/ImdbApiClient.java` (`search`):
  String-Konkatenation im Logging (`"Searching for: " + ...`) → parametrisiertes Logging.
  → erledigt (WerStreamtEsApiClient in TODO-9, ImdbApiClient hier).
- `web/StatusController.java`: `@GetMapping("public/status")` ohne führenden Slash
  (inkonsistent zu den übrigen Mappings). → erledigt: `@GetMapping("/public/status")`.

---

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

---

## Aus dem Re-Scan (2026-06-27, nach Umsetzung von TODO-6/7/8/9/17/18)

---

### ✅ TODO-19 — `/query` umgeht den Cache
`rest/QueryController.query(...)` ruft `werStreamtEsApiClient.query(...)` **direkt** auf
und scrapet damit bei jedem Aufruf live, während `/search` über `StreamInfoService` (gecacht) geht.
Inkonsistent und teuer.
- **Akzeptanzkriterium:** `/query` ebenfalls über `StreamInfoService.resolve(...)` laufen lassen
  (oder den Endpunkt entfernen, falls redundant zu `/search`).
- **Erledigt (obsolet):** `QueryController`/`/query` existiert nicht mehr.
  Der heutige Lookup-by-id-Endpunkt `GET /api/search?imdbId=` (`SearchApiController` →
  `SearchService.resolveByImdbId` → `StreamInfoService.resolve`) läuft bereits über den Cache.

---

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

---

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

---

### ✅ TODO-23 — `ResponseEntity<?>` mit rohem Wildcard
`rest/QueryController`: `query(...)` und `search(...)` geben `ResponseEntity<?>` zurück —
keine Typsicherheit für die Aufrufer/Tests.
- **Akzeptanzkriterium:** Konkrete Rückgabetypen (`ResponseEntity<List<QueryResult>>` o. ä.).
- **Erledigt (obsolet):** `QueryController` existiert nicht mehr;
  kein Controller im Codebase gibt heute noch `ResponseEntity<?>` zurück (`grep` liefert keine Treffer)
  — alle REST-Controller haben konkrete Rückgabetypen.

---

### ✅ TODO-24 — Tests für neue/ungetestete Service-Logik fehlen
Nach den Refactorings waren `PreCacheService`, `StreamInfoService.resolveAll(...)`
(Caching/Threshold/Batch-Miss-Fetch) und das atomare Reload-Verhalten von `ImdbEntryRepository`
nicht durch Unit-Tests abgedeckt.
- **Akzeptanzkriterium:** Gezielte Unit-Tests ergänzen (Mockito für die Repos/Clients).
- **Erledigt:** `ImdbEntryRepositoryTest`, `PreCacheServiceTest`, `StreamInfoServiceTest`
  (Cache-Hit/Miss/Expiry/forceRefresh/Batch).
  Beim Schreiben fiel TODO-28 auf.

---

### ✅ TODO-25 — Aggregat-Seiten berechnen bei jedem Request alles neu
`web/DataAggregateController` + `services/AggregateService`: jede Anbieter-Seite ruft `getAll()` auf
und löste damit sämtliche Einträge sequenziell auf (über TODO-11 hinaus,
das nur den doppelten `getAll()`-Aufruf der Amazon-Seite betrifft).
- **Akzeptanzkriterium:** Aggregat-Ergebnisse cachen/vorberechnen
  bzw. die Batch-Logik aus `resolveAll(...)` (TODO-11/#13 — der Verweis auf TODO-13 war falsch) wiederverwenden.
- **Erledigt:** `getAll()` nutzt jetzt `streamInfoService.resolveAll(...)`
  → eine Batch-Query statt N Einzelabfragen.
  (Echtes Aggregat-Caching bleibt als optionale spätere Optimierung offen.)

---

---

## Aus dem Re-Scan (2026-06-28)

---

### ✅ TODO-26 — Fehler-Logs ohne Query-Kontext
`services/WerStreamtEsApiClient`: Die `catch`-Blöcke in `query(...)` und `search(...)` loggten
`log.error("Not found %s".formatted(e.getMessage()))` bzw. warfen `new RuntimeException(e)`,
ohne anzugeben, **für welche Query/imdbId** der Fehler auftrat.
Bei den `parallelStream`-Läufen (Pre-Cache/Refresh) war so nicht nachvollziehbar,
welcher Eintrag fehlschlug.
- **Akzeptanzkriterium:** In allen Fehlerausgaben des Clients die betroffene Query
  (imdbId bzw. Suchbegriff) mitloggen.
- **Erledigt:** `query`/`search` loggen bzw. wrappen Fehler jetzt mit imdbId/Suchbegriff.

---

### ✅ TODO-29 — Requests/Sekunde gegen werstreamt.es begrenzen
Pre-Cache/Refresh feuern via `parallelStream` viele Requests gleichzeitig gegen werstreamt.es
— unhöflich und ein Block-Risiko.
- **Akzeptanzkriterium:** Outbound-Requests drosseln;
  konfigurierbares, sinnvoll vorbelegtes Property.
- **Erledigt:** `RateLimiter` (global, `synchronized`, mindest-Abstand zwischen Requests),
  `WerStreamtEsApiClient.query/search` rufen `acquire()` vor dem HTTP-Get;
  Property `wer-streamt.rate-limit.requests-per-second` (Default `2`, `<= 0` deaktiviert).

---

### ✅ TODO-28 — `forceRefresh` war invertiert (Refresh refetchte nie)
`services/StreamInfoService.resolve(imdbId, forceRefresh)`: Der Filter lautete `forceRefresh || isFresh(...)`.
Bei `forceRefresh == true` wurde der gecachte Eintrag dadurch **behalten** statt neu geholt
— d. h. die `/refresh/*`-Endpunkte (die `resolve(id, true)` aufrufen) aktualisierten die Daten nie.
- **Akzeptanzkriterium:** `forceRefresh == true` erzwingt einen erneuten Fetch.
- **Erledigt:** Bedingung zu `!forceRefresh && isFresh(...)` korrigiert;
  beim Schreiben der Tests (TODO-24) aufgefallen.
  Test `resolveForceRefreshAlwaysFetches` deckt es ab.

---

### ✅ TODO-27 — Liquibase einführen und DB-Schema als Changelog ablegen
Das Schema wurde von Hibernate per `ddl-auto=update` verwaltet (siehe auch TODO-10).
- **Akzeptanzkriterium:** Liquibase einbinden, das vollständige Schema als Changelog hinterlegen
  und `ddl-auto` auf `validate` umstellen,
  sodass das Schema reproduzierbar und versioniert ist.
  Dies ist auch die Voraussetzung für die Umbenennungen aus TODO-2/TODO-3.
- **Hinweis (Stand Juni 2026 — ⚠ heute nicht mehr befolgen):** Damals hielt die H2-DB
  ausschließlich gecachte Scrape-Ergebnisse, weshalb hier stand, man könne für bestehende
  Deployments das alte `./db` einfach entfernen.
  **Das würde heute Benutzerkonten, Watchlists, Sessions und Titel-Metadaten vernichten**
  — seit den Changesets `003`, `006` und `009` liegt all das in derselben Datenbank.
  Auch der genannte Endpunkt `/pre-cache` existiert nicht mehr (heute `POST /api/cache`).
- **Erledigt:** `spring-boot-liquibase` ergänzt;
  Baseline-Changelog unter `src/main/resources/db/changelog/`
  (`db.changelog-master.yaml` → `changes/001-baseline-schema.sql`),
  generiert aus dem Hibernate-Schema (inkl. der TODO-2/TODO-3-Namen);
  `ddl-auto=validate` in Haupt- und Test-Konfiguration — **seit TODO-40 `none`**.
  Tests laufen grün gegen das von Liquibase erzeugte Schema.
  Das Changelog ist inzwischen auf 18 Changesets gewachsen (zuletzt `018-drop-ebay-quota.xml`),
  die Baseline liegt als `.xml` vor, nicht als `.sql`.

---

---

## Architektur-Review (2026-06-28)

> **Lesehinweis (2026-09-09).** Klassen- und Paketnamen in den Einträgen dieses und der
> vorangehenden Abschnitte sind der Stand von Juni 2026.
> Der Umbau nach Bounded Contexts ([ADR-0014](docs/adr/0014-backend-nach-bounded-contexts-und-ports-adaptern.md))
> hat `services/`, `persistence/`, `web/`, `rest/` und `entities/` als Top-Level-Pakete abgelöst;
> heute gilt `<kontext>/{domain,application,port,adapter}` (siehe `CLAUDE.md`).
> Die Einträge wurden **nicht** durchgängig umbenannt — das wäre viel Rauschen für wenig Nutzen.
> Korrigiert ist, wo eine Aussage *sachlich* falsch geworden ist, nicht wo nur ein Pfad alt ist.

Vollständige Analyse: [`docs/reviews/2026-07-28-architecture-review.md`](docs/reviews/2026-07-28-architecture-review.md).
Die konkreten, umsetzbaren Punkte daraus:

---

### ✅ TODO-30 — `entities/` ist irreführend benannt
Das Paket `entities/` enthielt reine Domänen-Records (keine JPA-Entities);
die echten `@Entity`-Klassen liegen in `persistence/`.
- **Akzeptanzkriterium:** `entities/` → `domain/` umbenennen (ggf. `domainvalues/` hineinziehen);
  JPA-Entities bleiben in `persistence/`.
- **Erledigt:** `entities/` und `domainvalues/` zu `domain/` zusammengeführt
  (`ImdbEntry`, `QueryResult`, `SearchResult` + die `@Embeddable`-Werte `Availability`,
  `Price`, Enum `AvailabilityType`).
  JPA-`@Entity`-Klassen bleiben in `persistence/`.

---

### ✅ TODO-31 — `ImdbEntryRepository` ist kein Repository
Stateful In-Memory-Katalog, benannt wie ein Spring-Data-Repository und in `services/`.
- **Akzeptanzkriterium:** In `ImdbCatalog`/`WatchlistStore` umbenennen,
  klar von den Spring-Data-Repos in `persistence/` abgrenzen.
- **Erledigt:** Klasse → `ImdbCatalog` (Felder/Variablen/Bean-Methode `imdbCatalog`).

---

### ✅ TODO-32 — Nahezu identische Provider-Handler in `DataAggregateController`
`getDisney`/`getNetflix`/`getWow` (und `getAmazon`/`getGoogle`) unterschieden sich nur durch Service-/View-Namen.
- **Akzeptanzkriterium:** Datengetrieben zusammenfassen (Enum/Map aus Pfad → Service+View),
  ~4 Methoden auf eine reduzieren.
- **Erledigt, und seither weiter getrieben:** Heute gibt es genau **einen** Handler,
  `@GetMapping("/{provider}")` in `ProviderApiController`, aufgelöst über das Enum
  `StreamingProvider` (das die Datentabelle trägt) und `ProviderPageService`.
  Von den damaligen Helfern existiert nur noch `paidDtos(...)`.
  ~~(Explizite Routen beibehalten statt Catch-all-`{path}`, um Routing-Mehrdeutigkeit zu
  vermeiden.)~~ — **gilt nicht mehr:** es *ist* jetzt ein Catch-all, unproblematisch, weil
  `/api/providers` keine konkurrierende Route hat und unbekannte Keys mit 404 enden.

---

### ✅ TODO-33 — Transaktionsgrenze auf einem Controller
`DataAggregateController` war `@Transactional(readOnly = true)` auf Klassenebene.
- **Akzeptanzkriterium:** Transaktionsgrenzen in die Service-Schicht verschieben;
  Controller nicht transaktional.
- **Erledigt:** `@Transactional` von `DataAggregateController` **und** `ChangeListController` entfernt.
  DB-Zugriffe laufen über transaktionale Service-Methoden (`StreamInfoService.resolve/resolveAll`);
  die zurückgegebenen Records sind losgelöst, daher kein Open-Session-in-View nötig.
  (Nebeneffekt: Cache-Writes bei Miss laufen jetzt in einer Read-Write-Tx statt in einer Read-only-Tx.)

---

### ✅ TODO-34 — View-Model-Aufbau im Controller
`IndexDto`, `PaidDto` und `prettyPrint(...)` stecken im `DataAggregateController`.
- **Akzeptanzkriterium:** In einen Assembler/Formatter (oder DTO-Factory-Methoden) auslagern;
  Controller ruft nur noch den Assembler.
- **Erledigt (obsolet):** `DataAggregateController` existiert nicht mehr.
  Die heutigen Controller (`api/CatalogApiController`, `api/ProviderApiController`, …) sind dünn;
  die View-Model-Zusammenstellung sitzt in der Application-Schicht
  (`application/CatalogOverviewService`, `application/ProviderPageService`).

---

### ✅ TODO-35 — `invalidated`-Flag ist faktisch tot
`QueryMeta.invalidated` wurde nie auf `true` gesetzt, aber überall mitgefiltert.
- **Akzeptanzkriterium:** Invalidierung tatsächlich umsetzen (z. B. beim Refresh alte Zeilen invalidieren)
  **oder** Flag + Query-Suffix entfernen.
- **Erledigt:** Über TODO-38/TODO-39 zum Leben erweckt
  — `invalidateByImdbIds(...)` setzt das Flag;
  invalidierte Einträge gelten als „uncached" und werden gezielt neu gescraped.

---

### ✅ TODO-36 — Provider-Abstraktion fürs Scraping
Kein Interface über „Stream-Verfügbarkeits-Provider";
fest an jsoup/werstreamt.es gekoppelt (`ImdbApiClient` ist tot, siehe TODO-1).
- **Akzeptanzkriterium:** Interface `StreamAvailabilityProvider`
  (z. B. `List<QueryResult> query(String imdbId)`), implementiert von `WerStreamtEsApiClient`;
  Verbindungs-/User-Agent-/Rate-Limit-Belange dahinter bündeln.
- **Erledigt:** Interface `StreamAvailabilityProvider.query(imdbId)` eingeführt,
  von `WerStreamtEsApiClient` implementiert;
  `StreamInfoService` und `QueryController` hängen jetzt am Interface (Test mockt das Interface).

---

### ✅ TODO-37 — `AggregateService.getAll()` liefert `List<List<QueryResult>>`
Verschachtelte Form, die Aufrufer sofort flachklopfen.
- **Akzeptanzkriterium:** Flaches `List<QueryResult>` bzw. `Map` (wie `resolveAll`) zurückgeben;
  `included`/`paid` als ein Filter mit Prädikat.
- **Erledigt:** `getAll()` liefert flaches `List<QueryResult>`;
  `included`/`paid` teilen das Prädikat `on(serviceName)` (kombiniert mit `flatrate` bzw. dessen Negation).

---

---

## Invalidierungs-Feature (2026-06-28)

---

### ✅ TODO-38 — Einträge gezielt invalidieren (UI)
Eintrage in der UI auswählen und deren Cache invalidieren (für bewusstes Neu-Scrapen).
- **Akzeptanzkriterium:** Auswahl in der UI → markierte Einträge werden invalidiert.
- **Erledigt:** `QueryMetaRepository.invalidateByImdbIds(...)` (`@Modifying`),
  `PreCacheService.invalidate(...)`, Web-Endpunkt `POST /invalidate` und die `/manage`-Seite
  (Checkbox-Auswahl).
  Integrationstest gegen H2 + Mockito-Tests ergänzt.

---

### ✅ TODO-39 — Nur invalidierte/fehlende Einträge scrapen (UI)
Eine UI, die gezielt nur die invalidierten (bzw. nie gecachten) Einträge scrapt.
- **Akzeptanzkriterium:** Button/Endpunkt scrapt nur die Einträge ohne gültigen Cache.
- **Erledigt:** `PreCacheService.cacheUncached()` (nutzt `findUncached()`),
  Endpunkt `POST /scrape-invalidated`, Button auf `/manage`;
  Navbar-Link „Manage Cache".

---

---

## Bugfixes

---

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

---

## DB / Portierbarkeit

---

### ✅ TODO-40 — Liquibase-Changesets von SQL auf XML
Die Changesets waren H2-spezifisches Roh-SQL (`uuid`, `enum('BUY','RENT')`,
`timestamp(6) with time zone`) und damit nicht portabel.
- **Akzeptanzkriterium:** Changesets als XML mit dialekt-portablen Change-Types; Schema läuft
  auf H2 **und** MariaDB.
- **Erledigt:** `001-baseline-schema.xml` / `002-add-query-result-languages.xml`
  (`createTable`/`addColumn`/`addForeignKeyConstraint`); dialektabhängige Typen via
  `${uuid.type}`/`${timestamp.type}`-Properties. `ddl-auto=none` (Liquibase ist alleinige
  Schema-Quelle; Korrektheit über die Repository-Tests auf H2 + MariaDB).

---

### ✅ TODO-41 — MariaDB als First-Class-DB + Testcontainers
- **Akzeptanzkriterium:** MariaDB als unterstützte DB; Repository-Test-Suite läuft gegen eine
  Testcontainers-MariaDB.
> **Nachtrag 2026-09-08:** Die MariaDB-Tests laufen inzwischen **im normalen `mvn verify`** mit
> (409 statt 391 Tests). Sie tragen `@Tag("testcontainers")`; ausgeschlossen werden sie nur noch
> explizit per `-Pno-testcontainers`, u. a. in den Docker-Build-Stages, die keinen Docker-Socket
> haben. Der Text unten liest sich so, als liefen sie nur, wenn zufällig eine Container-Runtime
> da ist — die bewusste Entscheidung ist inzwischen das Gegenteil, weil ein Check, den man sich
> merken muss, ausfällt (`018-drop-ebay-quota` ging genau deshalb zunächst unverifiziert ein).
> `disabledWithoutDocker` bleibt als zweites Netz.

- **Erledigt:** MariaDB-Treiber, Profil `mariadb` (`application-mariadb.properties`),
  `mariadb`-Service in `compose.yml`. Repo-Tests in abstrakte Basen ausgelagert; je eine H2-
  und eine MariaDB-Variante (`@ServiceConnection MariaDBContainer`,
  `@Testcontainers(disabledWithoutDocker = true)` → ohne Container-Runtime übersprungen, nicht
  rot). H2 bleibt Default für Dev & In-Memory-Tests.

---

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

---

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

---

## Async Cache-Refresh statt synchronem Dashboard-Reload (2026-07-30/31)

Vollständiger Plan: [`docs/CACHE_REFRESH_PLAN.md`](docs/CACHE_REFRESH_PLAN.md),
Entscheidung: [ADR-0016](docs/adr/0016-asynchrone-verzoegerte-cache-aktualisierung.md).
Auslöser: die „Cache Verwalten"-Seite (`/manage`) hatte keinen beobachtbaren Effekt, weil
`StreamInfoService.resolveAll(...)` (Dashboard/Provider-Seiten) invalidierte/abgelaufene Einträge
synchron im selben Request nachlud — ein einziger Dashboard-Aufruf hob jede manuelle
Invalidierung sofort wieder auf, bevor die Manage-Seite etwas zu tun hätte.

---

### ✅ TODO-43 — Manage-Tabelle: Zeitstempel statt reinem „gecacht"-Boolean
`ManageRowDto`/`ManageTable` zeigten nur `needsScrape` (ja/nein), keinen Zeitpunkt.
- **Akzeptanzkriterium:** Pro Titel wird der Zeitpunkt des letzten Scrapes angezeigt (oder „nie"),
  nicht mehr nur ein binärer Pill; ein invalidierter Titel zeigt weiterhin „muss gescrapt werden".
- **Erledigt:** `QueryMetaRepository.findByImdbIdIn(...)` (ohne Invalidiert-Filter) + `ManageRowDto.lastScrapedAt`;
  `manage-table.ts` zeigt bei `needsScrape=false` den formatierten Zeitpunkt (Angular `DatePipe`)
  statt der bisherigen „gecacht"-Pill (`manage.statusCached` entfernt).
  Details: `docs/CACHE_REFRESH_PLAN.md`, Phase 1.

---

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

---

### ✅ TODO-45 — „Veraltet"-Banner auf Dashboard und Provider-Seiten
Es gab keine Kennzeichnung, wenn angezeigte Streaming-Verfügbarkeiten veraltet sind.
- **Akzeptanzkriterium:** Ein kleiner, seitenweiter Hinweis-Banner (kein Fehler) erscheint, wenn
  mind. ein angezeigter Titel `stale` ist (kein Per-Zeile-Flag, YAGNI).
- **Erledigt:** `CatalogPageDto` (`entries` + `hasStaleEntries`) und `ProviderPageDto.hasStaleEntries`;
  neue `StaleDataBanner`-Komponente (Vorlage `ErrorAlert`, eigenes Token
  `--mat-sys-secondary-container`) auf Dashboard und Provider-Seite eingebunden.
  Details: `docs/CACHE_REFRESH_PLAN.md`, Phase 3.

---

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

---

## Bug (2026-07-31)

---

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

---

## Feature (2026-07-31)

---

### ✅ TODO-48 — Sortierbarkeit der „Cache Verwalten"-Oberfläche
Die Manage-Tabelle (`/manage`, `ManageTable`) hatte keine Sortierung — anders als die
Verfügbarkeits-Tabellen (Dashboard/Provider-Seiten), die bereits per Klick auf die Spaltenüberschrift
nach Titel/Jahr/hinzugefügt sortierbar sind (`shared/sort/table-sort.ts`, `MatSortModule`).
- **Akzeptanzkriterium:** Die Manage-Tabelle lässt sich per Klick auf die Spaltenüberschrift nach
  **Name** und nach **Datum** (Zeitpunkt des letzten Scrapes, `lastScrapedAt` aus TODO-43) sortieren,
  auf- und absteigend, nach demselben Muster (`mat-sort-header`) wie die bestehenden Tabellen.
- **Erledigt:** Neue `sortManageRows(...)` in `shared/sort/table-sort.ts` (eigene kleine Funktion
  statt Erweiterung von `sortRows`, da die Manage-Tabelle weder `year` noch `added` hat); Zeilen, die
  gescrapt werden müssen (`needsScrape` — nie gecacht **oder** invalidiert), sortieren als
  frühestmöglicher Zeitpunkt (`-Infinity`): aufsteigend an den Anfang, absteigend ans Ende.

  **Umgedreht mit dem Folge-Fix `fc0c5fb`, und das ist der interessante Teil.** Ursprünglich
  wurde nur `lastScrapedAt = null` gesondert behandelt, und zwar genau andersherum — analog zum
  `year`-Sonderfall „Not yet released". Das ging an einem Fall vorbei, den dieses Ticket nicht
  kannte: ein **invalidierter** Titel behält seinen alten Zeitstempel. Er rutschte damit mitten
  in die Liste, obwohl die Status-Pille gar kein Datum anzeigt.
  Deshalb hängt die Sortierung heute an `needsScrape` statt an `lastScrapedAt === null`, und
  `SortableManageRow` trägt das Feld eigens dafür. `ManageTable` verdrahtet
  `MatSortModule`/`matSort` wie `CatalogTable`; die Status-Spalte trägt `mat-sort-header="lastScrapedAt"`
  (abweichend vom `matColumnDef`-Namen `status`), da sie sowohl die „muss gescrapt werden"-Pille als
  auch den Zeitstempel zeigt.

---

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

---

---

## eBay-Preisabfrage (2026-09-05)

---

### ✅ TODO-49 — Bestehende `save()`-Aufrufe auf Dirty Checking umgestellt (ADR-0018)
[ADR-0018](docs/adr/0018-dirty-checking-statt-explizitem-save.md) legt fest, dass innerhalb einer
Transaktion geladene Entitäten mutiert und **nicht** gespeichert werden — Hibernates Dirty Checking
schreibt beim Commit. Der Bestand folgt dem noch nicht; fünf Stellen rufen `save()` auf einer
bereits verwalteten Entität:

- `accountaccess/application/UserPreferencesService.java` — `update(...)`, das alle sechs
  Präferenz-Setter bündelt
- `accountaccess/application/UserAdminService.java` — zwei Stellen (`update`, `deactivate`)
- `titlecatalog/application/PosterService.java` — zwei Stellen (Zeile 151, 166)
- `titlecatalog/application/TitleMetaService.java` — Zeile 96
- `watchlist/application/WatchlistImportService.java` — Zeile 73 und 102

- **Akzeptanzkriterium:** Kein `save()` mehr auf einer Entität, die in derselben Transaktion
  geladen wurde. Die `of(...)`/`new`-Zweige derselben Methoden behalten ihren Aufruf — dort ist er
  zwingend.
- **Bewusst kein Sammel-Commit:** Nach der Pfadfinder-Konvention umzustellen, wer die Methode
  ohnehin anfasst. Die Änderung ist **nicht mechanisch** — bei jeder Stelle ist zu prüfen, ob die
  Entität wirklich in derselben Transaktion geladen wurde. Ein pauschales Streichen aller Aufrufe
  würde die tragenden mitreißen.
- **Risiko beim Umstellen:** Ein Fehler ist still. Wird eine Stelle umgestellt, deren Entität doch
  detached war, verfällt die Änderung ohne Exception und ohne Logeintrag. Mockito-Tests können das
  nicht aufdecken — sie sehen kein Dirty Checking. Wo das Schreiben die eigentliche Zusage ist,
  gehört ein Test gegen eine echte Persistenzschicht dazu.
- **Erledigt am 2026-09-05.** Sieben Aufrufe entfernt, einer bewusst behalten:
  `PosterService.storeBytes` lädt über `orElseGet(() -> TitlePoster.of(...))`, die Entität kann also
  verwaltet **oder** brandneu sein, und der eine `save` deckt beide Wege ab — ihn zu streichen hätte
  jedes erstmalige Poster still verloren. Genau der nicht-mechanische Fall, vor dem dieses Ticket
  warnt.
  Die Tests prüfen jetzt die Wirkung statt des Mechanismus (`verify(..., never()).save(any())` plus
  Zusicherung auf der mutierten Entität).
  Dazu `DirtyCheckingPersistenceTest`: ein nicht-transaktionaler `@SpringBootTest`, der nach dem
  Commit des Service neu liest — der einzige Test, der das Schreiben tatsächlich belegt. Verifiziert,
  dass er den Fehlerfall fängt: ohne `@Transactional` an der Service-Methode schlägt er fehl
  (`expected: DARK but was: SYSTEM`).

---

### ✅ TODO-50 — Indizes der Datenbank evaluiert
Bisher gibt es genau einen bewusst gesetzten Index (`014-index-query-cache-imdb-id.xml`); alles
andere sind Primärschlüssel und die Unique Constraints, die nebenbei einen Index mitbringen. Ob das
für die tatsächlichen Zugriffspfade reicht, ist **nie geprüft** worden — es ist eine Annahme, kein
Befund.

Anlass war die damals neue Quota-Tabelle aus [ADR-0017](docs/adr/0017-quota-verwaltung-fuer-die-ebay-browse-api.md)
(mit TODO-56 entfallen — die Evaluation der übrigen Indizes bleibt davon unberührt):
`ebay_user_quota_day` wird bei **jeder** Preisabfrage über `(quota_day, user_id)` gelesen. Der
Unique Constraint `uk_ebay_user_quota_day` deckt genau diese Kombination ab und trägt die Abfrage
damit vermutlich schon — aber „vermutlich" ist der Grund für dieses Ticket.

- **Akzeptanzkriterium:** Für die heißen Abfragepfade liegt ein `EXPLAIN`-Befund vor, aus dem
  hervorgeht, welcher Index benutzt wird bzw. wo ein Full Scan stattfindet. Fehlende Indizes werden
  als eigenes Liquibase-Changelog ergänzt, überflüssige benannt.
- **Kandidaten für die Prüfung:**
  - `ebay_user_quota_day` über `(quota_day, user_id)` — pro Preisabfrage
  - `watchlist_entry` über `user_id` — jeder Dashboard-Aufruf
  - `query_meta` über `due_for_refresh_at` — der gestaffelte Hintergrund-Refresh aus ADR-0016
    scannt danach
  - `title_meta` / `title_poster` über `imdb_id`
  - `spring_session` — kommt aus dem Spring-Session-Schema, nicht von uns, aber zu kennen
- **Zu bedenken:** Bei fünf Nutzern und wenigen tausend Zeilen wird der Optimizer manches ohnehin
  per Full Scan lösen, und zwar zu Recht. Das Ticket soll Indizes **begründet** setzen, nicht
  vorsorglich streuen — jeder Index kostet bei jedem Schreibvorgang.
- **Vorher zu klären:** Der Befund ist gegen **MariaDB** zu erheben, nicht gegen H2. Die
  Entwicklungs- und Testumgebung läuft auf H2, dessen Optimizer sich anders entscheidet.

**Erledigt am 2026-09-06.** Erhoben gegen MariaDB 12.3 in einem Wegwerf-Container, mit 2000
geseedeten `watchlist_entry`- und `query_meta`-Zeilen — leere Tabellen liefern nur
„Impossible WHERE" und damit keine Aussage.

| Zugriffspfad | Ergebnis |
| --- | --- |
| `ebay_user_quota_day` über `(quota_day, user_id)` | nutzt `uk_ebay_user_quota_day` — die Vermutung des Tickets bestätigt, **kein** zusätzlicher Index nötig |
| `watchlist_entry` über `(user_id, imdb_id)` | `type=const` über den zusammengesetzten Unique-Index |
| `watchlist_entry` über `user_id` allein | **`type=ALL`**, beide Indizes verfügbar, keiner gewählt |
| `query_meta` über `imdb_id` | nutzt `ix_query_meta_imdb_id` |
| `query_meta` über `due_for_refresh_at` | **`type=ALL`, `possible_keys=null`** — kein Index vorhanden |
| `app_user` über `username`, `query_result` über `imdb_id`, `title_meta`/`title_poster` über `imdb_id` | jeweils passender Unique-/Normalindex vorhanden und genutzt |

**Ein Kandidat gefunden, bewusst nicht umgesetzt:** `ix_watchlist_entry_user` ist überflüssig —
strukturell, weil `uq_watchlist_entry_user_imdb` `(user_id, imdb_id)` abdeckt und `user_id` dessen
linkes Präfix ist, und praktisch, weil der Optimizer ihn nachweislich nicht wählt (bei fünf Nutzern
trifft einer 20 % der Tabelle, da ist ein Full Scan billiger als 400 Index-Lookups). Jeder
Schreibvorgang pflegt ihn umsonst.

**Warum er trotzdem bleibt — der eigentliche Erkenntnisgewinn dieses Tickets:** die beiden
Datenbanken sind sich uneinig. MariaDB/InnoDB lässt den Drop zu, weil der Fremdschlüssel
`fk_watchlist_entry_user` seinen Pflichtindex im zusammengesetzten Unique-Index findet (geprüft:
`DROP` erfolgreich, Plan danach unverändert). **H2 verweigert ihn**
(`Index "IX_WATCHLIST_ENTRY_USER" belongs to constraint "FK_WATCHLIST_ENTRY_USER"`) — dort ist der
Index an die Zwangsbedingung gebunden. Ein Changeset, das nur auf MariaDB läuft, ließe
Entwicklungs- und Produktionsschema auseinanderlaufen; der Umweg über Fremdschlüssel löschen,
Index löschen, Fremdschlüssel neu anlegen wäre gegen beide Datenbanken zu verifizieren.

Für eine Tabelle mit wenigen tausend Zeilen und fünf Nutzern steht dieser Aufwand in keinem
Verhältnis zum eingesparten Index-Unterhalt. **Neu bewerten, wenn** `watchlist_entry` deutlich
wächst oder H2 als Entwicklungsdatenbank ohnehin abgelöst wird.

**Bewusst *kein* Index auf `due_for_refresh_at`.** Der gestaffelte Hintergrund-Refresh (ADR-0016)
scannt die Tabelle voll. Das ist heute richtig: die Tabelle hat eine Zeile je gecachtem Titel,
der Lauf ist geplant und nicht anfragegebunden, und ein Index würde jeden Scrape-Schreibvorgang
verteuern. **Neu bewerten, wenn** `query_meta` fünfstellig wird oder der Refresh-Lauf spürbar
dauert — dann ist er selektiv genug, um sich zu lohnen.

**Methodischer Hinweis für die nächste Runde:** die Aussagekraft hängt an realistischen Zeilenzahlen.
Mit leeren Tabellen hätte dieselbe Messung „alles bestens" ergeben und den Full Scan auf
`user_id` nicht gezeigt.

---

### ✅ TODO-51 — Eigenen Circuit Breaker durch resilience4j ersetzt *(Code inzwischen entfallen)*
> **Gegenstandslos seit 2026-09-07.** Alles hier Beschriebene lag im Kontext `purchaseoffers`
> und ist mit dem Rückbau der Preisabfrage (TODO-56) gelöscht — samt der Abhängigkeit
> `resilience4j-spring-boot4` und dem mit ihr gekommenen `micrometer-core`.
> Es gibt in der Anwendung derzeit **keinen** Circuit Breaker mehr, weil es keinen
> ausfallgefährdeten Fremddienst hinter einer Bean-Grenze mehr gibt.
>
> Der Eintrag bleibt wegen zweier Erkenntnisse, die den Code überdauern:
> die Versionsrecherche unten (das Boot-4-Artefakt heißt `2.4.0`, nicht `2.3.0`),
> und der Grund, warum die Konfiguration in Java statt in Properties gehörte.
> Beides gilt beim nächsten Anlauf unverändert.

`TitleOfferService` brachte einen handgeschriebenen Circuit Breaker mit: ein Zähler
aufeinanderfolgender Fehlschläge plus ein `openUntil`-Zeitpunkt, rund 15 Zeilen.

**Erste Bewertung war falsch und wurde korrigiert.** Zunächst hatte ich nur
`resilience4j-spring-boot4:2.3.0` geprüft, den Fehlschlag als „es gibt kein Boot-4-Artefakt"
gedeutet und deshalb zur Beibehaltung geraten. Tatsächlich existiert **`2.4.0`**. Damit fielen zwei
der drei Gegenargumente weg: das Artefakt zielt auf diese Boot-Generation, und es zieht **kein**
`kotlin-stdlib` nach (das kam vom `-spring-boot3`-Artefakt). Neu hinzu kommt nur `micrometer-core`,
das zugleich die Breaker-Metriken mitbringt; `micrometer-observation`/`-commons` waren über Spring
Boot ohnehin da.

- **Erledigt:** `@CircuitBreaker(name = "ebay")` auf `EbayBrowseApiSource.findOffers` — dort, wo
  tatsächlich mit eBay gesprochen wird, und an einer Bean-Grenze, die der Aspekt abfangen kann.
  `TitleOfferService` prüft den Zustand **vor** der Quota-Reservierung, damit ein kurzgeschlossener
  Aufruf nicht trotzdem zwei Calls vom Tagesbudget kostet, und behandelt
  `CallNotPermittedException` für das schmale Zeitfenster dazwischen.
- **Was der Wechsel behebt:** Der eigene Breaker zählte *aufeinanderfolgende* Fehlschläge und hätte
  bei einer Quelle, die jeden zweiten Aufruf ablehnt, **nie** ausgelöst — während die Hälfte des
  Budgets in Fehlschläge lief. Jetzt entscheidet die Fehlerrate über ein Sliding Window. Dazu kommt
  ein Half-Open-Zustand mit zwei Probeaufrufen statt der vollen Wiederöffnung.
- **Konfiguration in Java** (`EbayCircuitBreakerConfig`), nicht in `application.properties`, aus
  zwei Gründen, die beide beim Bauen auffielen: `ignoreExceptions` wäre dort ein voll
  qualifizierter Klassenname als **String**, dessen Tippfehler still auf die Defaults zurückfällt —
  ausgerechnet bei der Regel, die verhindert, dass ein erschöpftes Tagesbudget den Breaker öffnet.
  Und `src/test/resources/application.properties` **überschattet** die Produktionsdatei im
  Test-Classpath, womit Property-Werte für jeden `@SpringBootTest` unsichtbar sind.
- **Verifiziert:** `EbayCircuitBreakerConfigurationTest` prüft im echten Kontext, dass der
  Customizer greift (sonst stünden dort die resilience4j-Defaults 100/60 s) und dass eine
  erschöpfte Quota den Breaker nachweislich **nicht** öffnet, eine gewöhnliche Störung dagegen
  schon.

---

## Feature (2026-09-06)

---

### ✅ TODO-53 — Als Admin andere Nutzer impersonieren
Ein ADMIN soll die Anwendung vorübergehend als ein anderer Nutzer sehen können, um Meldungen
nachzuvollziehen, ohne sich dessen Passwort geben zu lassen.

- **Akzeptanzkriterium:** Ein ADMIN kann aus der Benutzerverwaltung heraus in die Sicht eines
  anderen Nutzers wechseln, sieht dessen Watchlist und Einstellungen, und kann den Wechsel wieder
  beenden — zurück in die eigene Sitzung, ohne erneute Anmeldung.
- **Naheliegender Weg:** Spring Security bringt dafür `SwitchUserFilter` mit
  (`/login/impersonate?username=…`, `/logout/impersonate`) — kein Eigenbau nötig. Der Filter legt
  die ursprüngliche Authentifizierung als `SwitchUserGrantedAuthority` ab, worüber der Rückweg
  läuft.
- **Was vor der Umsetzung zu klären ist — das ist hier der eigentliche Inhalt:**
  - **Wer darf wen?** Ein ADMIN, der einen anderen ADMIN impersoniert, ist ein Weg zur
    Rechteausweitung ohne Spur. Mindestens: keine Impersonierung von ADMINs, und niemals von sich
    selbst aus wieder hoch.
  - **Was ist sichtbar?** Ein Wechsel, den man nicht bemerkt, ist der gefährlichere Fehler. Die
    Oberfläche braucht einen dauerhaften, unübersehbaren Hinweis („Du siehst die Anwendung als
    …") mit dem Ausstieg direkt daneben.
  - **Was wird protokolliert?** Beginn und Ende jeder Impersonierung gehören ins Log, mit beiden
    Identitäten. Ohne das ist im Nachhinein nicht unterscheidbar, ob ein Nutzer etwas selbst getan
    hat oder ein Admin in seinem Namen.
  - **Was darf der Impersonierende tun?** Nur lesen oder auch schreiben? Schreiben in fremdem
    Namen ist der Punkt, an dem aus einem Diagnosewerkzeug eine Vertrauensfrage wird.
  - ~~**Wechselwirkung mit der eBay-Quota (ADR-0017):**~~ — mit dem Rückbau der Preisabfrage
    (TODO-56) gegenstandslos; `ImpersonationPort` ist ersatzlos entfallen, das Feature selbst
    unberührt. Der ursprüngliche Punkt lautete: Preisabfragen während einer Impersonierung
    werden auf das Kontingent des *impersonierten* Nutzers gebucht. Ob das gewollt ist, ist zu
    entscheiden — sonst verbraucht ein Admin fremdes Budget.
- **ADR-pflichtig**, sobald die Antworten stehen: es ist eine Sicherheitsentscheidung, keine
  Bedienkomfort-Frage.

**Erledigt am 2026-09-06**, dokumentiert in
[ADR-0020](docs/adr/0020-admin-impersonierung-ueber-switchuserfilter.md). Entscheidungen des
Auftraggebers: Schreiben ist erlaubt, ADMIN-Ziele sind ausgeschlossen, Preisabfragen sind während
einer Impersonierung gesperrt.

Zwei Punkte, die erst beim Bauen sichtbar wurden:

- **Der Rückweg darf nicht unter `/api/admin/**` liegen.** Während eines Wechsels trägt die Sitzung
  die Rollen des Ziels, also kein `ROLE_ADMIN` — ein Ausstieg hinter der Admin-Regel wäre
  ausgerechnet für die Sitzung geschlossen, die ihn braucht. Er liegt jetzt auf
  `/api/impersonate/exit` und ist über `ROLE_PREVIOUS_ADMINISTRATOR` geschützt, die Berechtigung,
  die nur eine gewechselte Sitzung hat.
- **Ein Nicht-Admin erfährt nichts.** Das Banner hängt allein an `impersonatedBy` aus `/api/me`, und
  dieses Feld ist für einen gewöhnlichen Nutzer immer `null`. Die Startaktion liegt in der
  Benutzerverwaltung, die ohnehin ADMIN-only ist.

---

---

## Build-Toolchain (2026-09-06)

---

### ✅ TODO-55 — `upgrade-spring-boot.sh` gehärtet
Der nächtliche Lauf hat die Anwendung lahmgelegt. Drei Ursachen, alle im Skript:

1. **Der Rollback rollt nicht zurück.** `handle_error()` ruft
   `git reset --hard "$CURRENT_HEAD"` — **`CURRENT_HEAD` wird nirgends gesetzt** (einzige
   Fundstelle im Skript ist diese Verwendung). Der Befehl scheitert an einem leeren Argument, der
   Reset findet nicht statt, und die von `versions:update-parent` geänderte `pom.xml` bleibt im
   Arbeitsbaum liegen. Der nächste Lauf startet auf einem verschmutzten Baum, `update-and-restart.sh`
   bricht bei `git pull --rebase` ab — **die gesamte Update-Kette steht, bis jemand von Hand
   aufräumt.** Das ist der eigentliche Grund, warum ein einzelner fehlgeschlagener Build zum
   Dauerzustand wurde.
2. **Getestet wird gegen die Toolchain des Hosts, ausgeliefert wird aus Docker.**
   `mvn clean package` im Skript nutzt Node/npm des Hosts; der spätere `docker build` nutzt
   `node:24-alpine`. Eine Node-Aktualisierung auf dem Host bricht damit den Prüflauf, obwohl das
   Artefakt selbst gebaut werden könnte — und umgekehrt kann der Prüflauf grün sein, während der
   Docker-Build scheitert. Die beiden sollten dieselbe Toolchain benutzen.
3. **Milestones und RCs werden automatisch gezogen** (TODO-13). Das ist inzwischen als gewollt
   entschieden — es macht den Punkt (1) allerdings umso wichtiger: ein instabiler Parent lässt den
   Build häufiger scheitern, und ohne funktionierenden Rollback bleibt jeder dieser Fehlschläge
   stehen.

- **Akzeptanzkriterium:**
  - `CURRENT_HEAD="$(git rev-parse HEAD)"` **vor** der ersten Änderung setzen; zusätzlich im
    Fehlerfall `git checkout -- pom.xml` als Gürtel-und-Hosenträger.
  - Der Prüflauf verwendet dieselbe Node-Version wie der Docker-Build (entweder im Container
    bauen oder die Version aus einer gemeinsamen Quelle beziehen, siehe TODO-54).
  - ~~Kein automatisches Update auf Milestones/RCs~~ — verworfen, siehe TODO-13.
  - Ein fehlgeschlagener Lauf hinterlässt einen **sauberen** Arbeitsbaum — prüfbar, indem man den
    Fehlerfall einmal absichtlich auslöst.
- **Nicht verifiziert:** Welcher der drei Punkte den konkreten Ausfall ausgelöst hat, lässt sich
  von hier aus nicht sagen — die Fehlermeldung des Hosts liegt nicht vor. Punkt 1 erklärt
  allerdings, warum aus einem einmaligen Fehlschlag ein Dauerzustand wurde, unabhängig davon, was
  ihn ausgelöst hat.

**Teilweise erledigt am 2026-09-06:**

- ✅ Punkt 1: `CURRENT_HEAD` wird vor der ersten Änderung gesetzt, mit Abbruch falls leer, plus
  `git checkout -- pom.xml` im Fehlerpfad. Ein fehlgeschlagener Lauf hinterlässt damit einen
  sauberen Arbeitsbaum.
- ✅ Punkt 2: Der Prüflauf geht über `docker build . --target verify` statt `mvn clean package`
  auf dem Host. Prüfung und Auslieferung leiten sich jetzt von derselben `toolchain`-Stufe im
  Dockerfile ab.
- ✅ Punkt 3: Milestones/RCs weiterhin automatisch — auf Entscheidung des Auftraggebers so
  gewollt, siehe TODO-13. Der Punkt entfällt damit als Mangel.
- ✅ Nachgezogen am 2026-09-06: Die ausgelieferte `builder`-Stufe baut jetzt **mit** Tests. Ohne
  das wäre jeder gewöhnliche Commit ungetestet in den Betrieb gegangen — der nächtliche Prüflauf
  greift ja nur, wenn Spring Boot selbst etwas veröffentlicht hat.
- ✅ `cron.sh` entkoppelt: `upgrade-spring-boot.sh` unterscheidet jetzt „nichts zu tun" (Exit 2)
  von „kaputt" (Exit 1). Vorher brach die Kette in jeder Nacht ohne Spring-Boot-Release ab, und
  `update-and-restart.sh` lief nie — die Anwendung wurde also nur dann neu ausgerollt, wenn
  zufällig auch Spring Boot etwas veröffentlicht hatte.
- ✅ **Nachgetragen 2026-09-09:** Der Docker-Build ließ sich in der Entwicklungsumgebung nicht
  ausführen (Podman bekam dort kein Netzwerk auf). Inzwischen ist der Proxy-Fix aus TODO-61 auf
  dem Host ausgerollt und die Anwendung läuft — der Build ist also durchgelaufen.
  **Der Beleg ist indirekt**: bestätigt ist, dass gebaut und ausgerollt wurde, nicht dass jeder
  Zweig des Skripts einmal gelaufen ist. Ungeprüft bleibt insbesondere der Fehlerpfad
  (`handle_error`) — den sieht man erst, wenn ein Upgrade wirklich scheitert.


---

---

## eBay-Rückbau und Ersatz (2026-09-06)

---

### ✅ TODO-56 — eBay-Preisabfrage zurückgebaut
Die Preisabfrage über die Browse API (ADR-0017) **lief** — der Developer-Account war
freigeschaltet, das Feature war in der Anwendung in Benutzung.
Es hat sich im Betrieb als nicht gut genug erwiesen und wird deshalb zurückgebaut.
Der Stand ist auf dem Branch `feature/ebay_search` festgehalten —
hier geht nichts verloren, nur aus `dev` raus.

**Woran es scheiterte** (aus dem Betrieb, 2026-09-09) — der wichtigste Satz dieses Eintrags,
weil er den Nächsten davon abhält, dasselbe noch einmal zu bauen:

1. **Intransparent.** Man sah eine Zahl, aber nicht, *worauf* sie sich bezog.
   Ein Preis ohne das Angebot dahinter ist nicht nachprüfbar —
   der Nutzer muss ihn glauben oder ignorieren.
2. **Zu viele Variablen im Angebot.** Zustand und Qualität des Mediums, DVD oder Blu-ray,
   Director's Cut oder Kinofassung, Sammleredition, Sprachfassung.
   „Günstigster Preis" fasst Dinge zusammen, die keine Alternativen zueinander sind.
   Die billigste Scheibe ist regelmäßig nicht die, die jemand haben will.

**Das ist kein Umsetzungsfehler, sondern ein Modellierungsfehler** — und er war unabhängig von
der API-Variante: Für physische Medien ist „ein Preis je Titel" die falsche Abstraktion.
Kein Kontingent, kein besserer Suchbegriff und keine Anbieterwahl hätte daran etwas geändert.

Genau deshalb ist der Ersatz (TODO-57) nicht bloß die billigere Lösung, sondern die ehrlichere:
Der Suchlink beantwortet die Preisfrage gar nicht, sondern stellt den Nutzer vor die
Angebotsliste, in der diese Variablen sichtbar sind — und überlässt die Entscheidung dem
Menschen, der als einziger weiß, welche Ausgabe gemeint ist.
**Die Marktplatz-Auswahl pro Nutzer bleibt**, weil der Ersatz (TODO-57) sie braucht.

**Zuerst klären — zwingend vor dem Löschen, sonst startet die Anwendung nicht mehr:**
Die bleibende Marktplatz-Auswahl hängt an zwei Dingen, die im wegfallenden Kontext liegen.

1. Das Enum `Marketplace` (EBAY_DE/EBAY_US/EBAY_GB mit Marktplatz-Id, Währung, Basisdomain)
   liegt in `purchaseoffers/domain`.
2. `MarketplaceCatalog` (`purchaseoffers/adapter/in/spi`) implementiert
   `accountaccess.port.spi.SupportedMarketplaces` — und ist die **einzige** Implementierung.
   Fällt sie ersatzlos weg, findet Spring keine Bean für den SPI,
   `UserPreferencesService` bekommt sie injiziert, und **der Anwendungskontext startet nicht mehr**.
   Zusätzlich entfiele die Validierung der Marktplatz-Eingabe —
   `app_user.ebay_marketplace` würde zum Freitextfeld.

**Geklärt durch TODO-57 (2026-09-06):** Der Deep-Link entsteht vollständig im Client,
aus `EbayMarketplace` in `core/models.ts`.
Das Backend liefert dazu nichts — es braucht `Marketplace` nur noch, um die Einstellung eines
Nutzers zu validieren.
Damit gehört das Enum in `accountaccess`, den Kontext, dem die Einstellung ohnehin gehört.

Das erledigt Punkt 2 gleich mit: liegt das Enum dort, gibt es keine kontextübergreifende
Abhängigkeit mehr, die umzudrehen wäre — `SupportedMarketplaces` (`accountaccess/port/spi`)
und `MarketplaceCatalog` entfallen beide ersatzlos,
statt in einen anderen Kontext verschoben zu werden.
Die eingeführte Inversion war an die Preisabfrage gebunden und verschwindet mit ihr;
[ADR-0019](docs/adr/0019-port-spi-fuer-umgekehrte-kontextabhaengigkeiten.md) bleibt gültig,
verliert hier aber ihren zweiten Anwendungsfall
(`PosterAttributionProvider` bleibt der erste).

**Bestandsaufnahme (erhoben, vollständig):**

| Bereich | Entfällt |
| --- | --- |
| Backend | Ganzer Bounded Context `purchaseoffers`: `domain` (`Offer`, `OfferPrice`, `TitleOffers`, `OfferLookupResult`, `QuotaDay`, `QuotaVerdict`, `GlobalQuotaUsage`, `UserQuotaUsage`, `OfferSourceUnavailableException`, `UpstreamQuotaExhaustedException`, `Marketplace` — siehe oben), `application` (`TitleOfferService`, `QuotaService`, `dto/OfferDto`, `dto/TitleOffersDto`), `port/out` (`PurchaseOfferSource`, `GlobalQuotaUsageRepository`, `UserQuotaUsageRepository`), `adapter/out/ebay` (`EbayBrowseApiSource`, `EbayOAuthTokenProvider`, `EbayProperties`, `EbayCircuitBreakerConfig`), `adapter/in/api/PurchaseOfferApiController`, `adapter/in/spi/MarketplaceCatalog` — plus alle zugehörigen Tests |
| Frontend | **Achtung:** neben jeder dieser Stellen steht seit TODO-57 ein gleichnamiger `showEbayLink`-Eingang, der **bleibt** — er trägt den Suchlink. Ein `grep`-getriebener Rückbau löscht ihn mit, und weil Angular-Inputs einen Default haben, fällt das weder beim Kompilieren noch zur Laufzeit auf: der Link verschwindet still. Es entfallen: `core/api/offers-api.ts`, `core/offers-store.ts`, `shared/offer-prices/` (Komponente + Spec), der `showOffers`-Eingang samt eBay-Spalte in `shared/catalog-table/catalog-table.ts`, der Chip in `shared/title-tile/title-tile.ts`, das Durchreichen in `shared/title-grid/title-grid.ts` (die Aktivierung in `features/overview/overview-page.ts` ist bereits entfernt — siehe TODO-57), die Typen `Offer`/`TitleOffers`/`OfferStatus` in `core/models.ts`, der i18n-Block `offers.*` in `i18n/de.json` und `i18n/en.json`, der Spaltenkopf `table.offers` |
| Konfiguration | `ebay.*`-Block in `application.properties` (**ohne** `ebay.default-marketplace` — der gehört zur bleibenden Auswahl), eBay-Abschnitt in `.env.example`, die beiden `EBAY_*`-Zeilen in `compose.yml`, Abhängigkeit `io.github.resilience4j:resilience4j-spring-boot4` samt `resilience4j.version`-Property in `pom.xml` (wird ausschließlich von diesem Feature genutzt) |
| ArchUnit | Regel `purchaseoffers_is_only_accessed_through_its_published_ports`; `purchaseoffers` fliegt aus der Paketliste von `spring_data_repositories_are_the_port_not_the_adapter`. `bounded_contexts_are_free_of_cycles` bleibt. |

**Datenbank:** Liquibase `016-ebay-quota.xml` legt `ebay_quota_day` und `ebay_user_quota_day` an.
Angewendete Changesets dürfen nicht aus dem Changelog gelöscht werden —
es braucht ein **neues** Changeset, das beide Tabellen droppt.
`017-user-ebay-marketplace.xml` bleibt unangetastet.

**Weiteres, das mitzuziehen ist:**

- `accountaccess.port.in.ImpersonationPort` wurde nur für die Sperre der Preisabfrage
  während einer Impersonierung eingeführt und hat danach keinen Aufrufer mehr —
  entfällt, ebenso der Status `IMPERSONATION_ACTIVE` und der i18n-Schlüssel `offers.impersonating`.
- [ADR-0017](docs/adr/0017-quota-verwaltung-fuer-die-ebay-browse-api.md) wird gegenstandslos →
  auf `Superseded` setzen, nicht löschen.
  [ADR-0020](docs/adr/0020-admin-impersonierung-ueber-switchuserfilter.md) verweist auf die
  Quota-Wechselwirkung → diesen Absatz anpassen.
- `docs/EBAY_PRICE_LOOKUP_PLAN.md` bleibt als historisches Dokument,
  bekommt aber einen Statushinweis, dass das Vorhaben eingestellt wurde.
- TODO-51 (resilience4j) und TODO-52 (Bundle-Größe) beziehen sich teilweise auf entfallenden Code;
  TODO-52 wird durch den Rückbau eher besser.

- **Akzeptanzkriterium:** im Produktionscode bleibt von eBay nur die Marktplatz-Auswahl und der
  Suchlink; der Anwendungskontext startet; alle Tests grün;
  die beiden Quota-Tabellen sind per Changeset entfernt.
  (Das ursprüngliche „`grep -ri ebay` trifft nichts mehr" war so nicht erreichbar:
  die Changelogs `016`–`018` sind append-only und bleiben Treffer.)

**Erledigt am 2026-09-07, in fünf Schritten mit je einem grünen Build.**
Der Plan wurde vorher von einem Subagenten gegen den Code geprüft;
drei seiner Korrekturen haben die Umsetzung verändert:

1. **`ebay.default-marketplace` bleibt nicht.**
   Der Plan nahm an, das Property gehöre zur bleibenden Auswahl.
   Tatsächlich las es nur `EbayProperties` → `TitleOfferService`;
   der Default der Auswahl steht in `AppUser` und `UserPreferences`.
   Es entfällt samt `EBAY_DEFAULT_MARKETPLACE` in `compose.yml` und `.env.example`.
2. **Zwei Ports, die der Plan nicht nannte, wurden aufruferlos:**
   `UserDirectoryPort` (+`UserDirectoryService`) und `ImpersonationPort` (+`ImpersonationService`).
   Beide entfallen; das Impersonierungs-Feature selbst ist unberührt,
   weil `MeApiController` `ImpersonationConfig` direkt liest.
3. **Die Bestandsaufnahme war an vier Stellen doppeldeutig geworden:**
   neben jedem `showOffers` steht seit TODO-57 ein `showEbayLink`,
   neben der `offers`-Spalte eine `ebay`-Spalte, neben `.offer-chip` ein `.ebay-chip`.
   Ein `grep`-getriebener Rückbau hätte den Ersatz mitgerissen.

**Auch gegen MariaDB verifiziert** (nachgeholt am 2026-09-08):
`mvn -Ptestcontainers verify` läuft in diesem Container — es ist ein Maven-**Profil**,
kein `-Dgroups`, weshalb der erste Versuch null Tests fand.
409 statt 391 Tests, alle grün.
Da `spring.jpa.hibernate.ddl-auto=none` gilt, kann das Schema nur von Liquibase stammen:
der Kontext ist gegen ein echtes MariaDB gestartet, also hat Liquibase das komplette
Changelog inklusive `018` angewandt.
Ein direkter Logeintrag je Changeset liegt nicht vor (Liquibase loggt das auf Default-Level nicht) —
der Beleg ist der erfolgreiche Kontextstart, nicht die Zeile.

---

### ✅ TODO-57 — eBay-Suchlink pro Titel auf dem Dashboard
Der Ersatz für die zurückgebaute Preisabfrage (TODO-56) — im ursprünglichen Plan
([`docs/EBAY_PRICE_LOOKUP_PLAN.md`](docs/EBAY_PRICE_LOOKUP_PLAN.md), Abschnitt 4) war das
**Variante A**, dort bewertet als „trivial, minimales Risiko, Stunden statt Tage".

Neben jedem Titel auf dem Dashboard steht ein Link, der in einem neuen Tab die eBay-Suche öffnet:
Suchbegriff `"<Titel> <Erscheinungsjahr>"`,
Marktplatz nach der Einstellung des Nutzers (`ebayMarketplace` aus `/api/me`,
EBAY_DE/EBAY_US/EBAY_GB → `ebay.de`/`ebay.com`/`ebay.co.uk`).

**Warum das funktioniert, wo der Vorgänger scheiterte:**
Der Link wird im Client gebaut — **kein Server-Aufruf, keine Quota, kein Account,
kein Circuit Breaker**.
Es gibt nichts, was eine Freischaltung voraussetzt.

**Die Randbedingung des Vorgängers gilt hier nicht mehr, und das ändert den Entwurf.**
Beim Preisabruf kostete jede Anfrage zwei Calls aus einem geteilten Tagesbudget — deshalb war die
Suche dort auf *ein* Ergebnis optimiert und jede Verfeinerung ein Kompromiss.
Ein Link kostet nichts, weder beim Bauen noch beim Nichtanklicken.
Die Suchanfrage darf deshalb so gut sein, wie wir sie hinbekommen, statt so sparsam wie möglich.

- **URL-Form:** `https://www.<domain>/sch/i.html?_nkw=<urlencodierter Suchbegriff>`.
  **Nicht verifiziert** — aus der Recherche des alten Plans übernommen.
- **Kategoriefilter `&_sacat=…` — der größte Hebel auf die Trefferqualität, und der einzige Punkt,
  der vor der Umsetzung geprüft gehört.**
  Der alte Plan nannte `617` („DVDs & Blu-ray Discs") für `ebay.de`.
  Ohne ihn findet „Heat" Heizungszubehör, mit ihm Filme.
  **Zweifach unverifiziert:** weder ist die Zahl bestätigt, noch dass sie auf `ebay.com` und
  `ebay.co.uk` dieselbe ist — eBay-Kategorie-Ids sind nicht garantiert marktplatzübergreifend
  identisch.
  Das ist in Minuten von Hand nachzusehen, indem man die Suche auf jedem der drei Marktplätze
  einmal aufruft.
  Falls die Ids abweichen, gehört die Kategorie zur Marktplatz-Zuordnung, nicht in eine Konstante.
- **Deutscher Titel, wenn vorhanden und passend.**
  Eine Suche auf `ebay.de` nach „Der Pate 1972" trifft besser als nach „The Godfather 1972".
  Der deutsche Titel liegt in `TitleMeta.germanTitle` und ist im Client über `injectTitleMeta`
  erreichbar — **aber nur, wenn er ohnehin schon geladen wird**, also wenn Altersfreigaben oder
  deutsche Titel eingeschaltet sind (`title-meta.ts` holt sonst nichts).
  **Regel:** deutscher Titel nur für `EBAY_DE` und nur, wenn er ohne Zusatzabruf verfügbar ist;
  sonst `OverviewEntry.name`.
  **Ausdrücklich keinen Abruf allein für den Link auslösen** — eine Anfrage je Titel beim
  Seitenaufbau ist genau das, was dieser Ersatz loswerden soll.
- **Sortierung nach Gesamtpreis, aufsteigend — `&_sop=15` („Preis + Versand: niedrigste zuerst").**
  Kein Extra, sondern der Punkt, an dem der Link das ersetzt, was die Preisabfrage leisten sollte:
  Die alte Funktion beantwortete „was kostet das mindestens?" mit einer Zahl,
  der Link beantwortet dieselbe Frage mit dem ersten Treffer der Liste.
  Ohne Sortierung landet man auf eBays Relevanz-Reihenfolge, und die Antwort steht irgendwo.
  Dass die Sortierung Versand einschließt, ist dabei die eigentliche Übereinstimmung —
  auch der alte Vergleich rechnete Preis plus Versand
  (`offerTotalCents` im entfallenden `offer-prices.ts`).
  **Der Wert `15` ist nicht verifiziert.** Beim Prüfen des Kategoriefilters mit abhaken —
  es ist derselbe Handgriff. Falls er nicht stimmt: den Parameter weglassen, nicht raten.
- **Kein Link bei noch nicht erschienenen Titeln.**
  `ReleaseYear` nutzt `0` für „noch nicht erschienen/unbekannt".
  Das alte Feature ließ in diesem Fall nur das Jahr weg und suchte trotzdem
  (`TitleOfferService.searchTermFor`) — hier ist der Absprung stattdessen ganz wegzulassen:
  was nicht erschienen ist, wird auch nicht verkauft, und eine Suche nach dem blanken Titel
  liefert dann bestenfalls Rauschen.
  Das erspart zugleich den Sonderfall im Suchbegriff — er enthält immer Titel **und** Jahr,
  oder es gibt ihn nicht.
  Zu entscheiden bleibt die Darstellung: gar nichts rendern oder ein deaktivierter Hinweis.
  Vorschlag: gar nichts, damit die Zeile ruhig bleibt.
- **Darstellung:** `<a target="_blank" rel="noopener">` wie der bestehende IMDb-Link,
  aber **nicht** in der Titelzelle:
  ein Inline-Link säße über ein paar hundert Zeilen in jeder an einer anderen x-Position
  und wäre nicht mehr scanbar.
  In der Tabelle deshalb eine eigene Spalte, im Grid eine Badge unter der Kachel.
  Nur Dashboard, nicht auf den Provider-Seiten.
  Beide Komponenten werden von den Provider-Seiten mitgenutzt, die Abgrenzung braucht also einen
  ausdrücklichen Eingang (wie zuvor `showOffers`) und ergibt sich nicht von selbst.
  Die Begründung ist diesmal aber eine andere: kein Budget, das zu schützen wäre, sondern schlicht,
  dass der Absprung dort nicht hingehört.
- **Barrierefreiheit:** der Link braucht einen zugänglichen Namen, der den Titel nennt.
  Zweihundert Zeilen mit dem identischen Linktext „eBay" sind mit einer Vorlesehilfe unbenutzbar.
  Muster wie beim alten `offers.loadFor`: sichtbarer Kurztext, `aria-label` mit dem Titelnamen.
- **i18n** in `de.json` und `en.json`, Schlüssel parallel halten.
- **Tests (Vitest):** URL-Bildung als reine, testbare Funktion
  (Marktplatz-Zuordnung, Sonderzeichen im Titel korrekt kodiert);
  Rendering mit `rel="noopener"`, und dass bei `year = 0` **kein** Link entsteht.
- **Kein CSP-Problem:** ein `<a href>` ist eine Navigation und wird von den Fetch-Direktiven
  der Content-Security-Policy nicht erfasst.

- **Akzeptanzkriterium:** Ein Klick neben einem erschienenen Titel öffnet in einem neuen Tab
  die eBay-Suche des eingestellten Marktplatzes nach Titel und Jahr,
  aufsteigend nach Preis inklusive Versand sortiert.
  Bei einem noch nicht erschienenen Titel gibt es keinen Absprung.

**Umgesetzt (2026-09-06).**
`core/ebay-search.ts` baut die URL als reine Funktion; die Marktplatz-Tabelle dort hält Host,
Kategorie und die Frage, ob der deutsche Titel hier der bessere Suchbegriff ist —
je Marktplatz, nicht als eine Konstante, damit eine falsche Kategorie-Id eine Zeile kostet.
Dargestellt wird er von `shared/ebay-link/` in zwei Erscheinungsformen —
in der Tabelle als Wort in einer **eigenen Spalte**,
im Poster-Grid als **Badge mit dem eBay-Schriftzug** in den vier Markenfarben.
Eine Komponente für beides, damit die zwei Ansichten nicht auseinanderlaufen
und dieselbe Zeile nicht je nach Ansichtsmodus etwas anderes sucht.
`CatalogTable` und `TitleTile` bekommen je einen `showEbayLink`-Eingang (aus, sofern nicht gesetzt);
nur `OverviewPage` schaltet ihn an.
`TileEntry` trägt jetzt zusätzlich `releaseYear: ReleaseYear | null` —
für die Provider-Kacheln null, weil `PaidEntry` das Jahr nur als fertigen Text liefert
und ein zurückgerechnetes Jahr ein erfundenes wäre.

Die beiden eBay-Parameter (`_sacat=617`, `_sop=15`) sind unter **TODO-58** geprüft
und bestätigt — über `m.ebay.*`, weil `www.ebay.*` automatisierte Anfragen mit `403` abweist.

**Nachgezogen nach dem Review (2026-09-07):** siehe Commit „Review-Anmerkungen zu TODO-57".
Wesentlich: der deutsche Titel hing an der Altersfreigaben-Einstellung
(und wechselte nach dem Laden der Metadaten unter dem Cursor);
die Verdrahtung über drei Komponenten war ungetestet;
und das Dashboard zeigte den Suchlink **neben** der alten Preisabfrage —
zwei Bedienelemente namens „eBay" pro Zeile.
`showOffers` ist deshalb auf dem Dashboard bereits abgeschaltet;
der Code selbst entfällt mit TODO-56.

---

### ✅ TODO-58 — eBay-Suchparameter `_sacat` und `_sop` verifiziert
Der Suchlink aus TODO-57 trägt zwei Werte, die nie gegen eBay geprüft wurden:
die Kategorie `_sacat=617` („DVDs & Blu-ray Discs")
und die Sortierung `_sop=15` („Preis + Versand, niedrigste zuerst").

**Herkunft:** `617` stammt aus dem Default der zurückgebauten Preisabfrage
(`EbayProperties.categoryId`) — dort war er ebenso ungeprüft.
Aus der Containerumgebung ist das nicht nachzuholen:
eBay beantwortet automatisierte Anfragen mit `403`.

**Warum das nicht kosmetisch ist:** Der Link sortiert nach dem niedrigsten Gesamtpreis.
Ohne wirksamen Kategoriefilter steht damit nicht das billigste *Exemplar des Films* oben,
sondern der billigste Treffer überhaupt — ein Poster, eine Leerhülle, ein Schlüsselanhänger.
Der Filter trägt hier mehr Last als in einer relevanzsortierten Liste.

**Zu tun** (Minuten, von Hand, im Browser):
1. Je Marktplatz (`ebay.de`, `ebay.com`, `ebay.co.uk`) eine Suche aufrufen und prüfen,
   ob `_sacat=617` dort dieselbe Kategorie meint.
   eBay garantiert Kategorie-Ids **nicht** marktplatzübergreifend.
2. Prüfen, ob `_sop=15` tatsächlich nach Preis inklusive Versand aufsteigend sortiert.
3. Ergebnis in `core/ebay-search.ts` eintragen.
   Die Tabelle dort ist je Marktplatz aufgebaut, eine Korrektur kostet eine Zeile;
   `categoryId: null` schaltet den Filter für einen einzelnen Marktplatz ab.

**Erledigt am 2026-09-07 — beide Werte stimmen, der Code bleibt unverändert.**

Der Weg über `www.ebay.*` scheitert weiter an `403`.
Die **mobile** Domain `m.ebay.*` antwortet dagegen mit einer echten Trefferseite
(200, ~100 kB, kein Captcha) — dieselben Parameter, dieselbe Suchmaschine.
Das ist der Handgriff, der beim nächsten Mal Zeit spart.

| Prüfung | Ergebnis |
| --- | --- |
| `_sacat=617` auf `.de` | ✅ Seitentitel: „DVDs & Blu-rays \| eBay" |
| `_sacat=617` auf `.com` | ✅ Seitentitel: „Heat 1995 **in DVDs & Blu-ray Discs** for sale" |
| `_sacat=617` auf `.co.uk` | ⚠️ wirksam, aber nie benannt: Filmsuche 296 → 193 Treffer, „kettle" 32.000 → 370. Konsistent mit derselben Kategorie, **direkt belegt ist es nicht** |
| `_sop=15` auf `.de` | ✅ erste Treffer 1,50 / 2,49 / 3,00 / 3,90 € zzgl. Versand, aufsteigend |
| `_sop=15` auf `.com` | ✅ Reihenfolge kippt gegenüber derselben Suche ohne den Parameter sichtbar zu den günstigeren Angeboten |

**Ein Nebenbefund, der eine Notiz wert ist:** `_sop=12` heißt **nicht** aufsteigend.
Es war der naheliegende Kandidat — die Menü-Beschriftung „Niedrigster Preis inkl. Versand"
steht im Markup direkt daneben — und liefert doch eine ungeordnete Liste
(4,99 / 58,50 / 5,99 / 2,00 €).
Wer den Wert künftig „korrigiert", macht es schlechter.
Steht deshalb als Warnung im JSDoc.

Die Werte bleiben Magie aus einem fremden System:
`ebay-search.spec.ts` schreibt sie fest, damit eine Änderung eine bewusste Handlung ist.
Die Prüfung hindert eBay nicht daran, morgen umzunummerieren.

---

### ✅ TODO-61 — Nach abgelaufener Sitzung führt ein erfolgreicher Login nicht zum Dashboard
**Gemeldet am 2026-09-08 aus dem Betrieb.**

**Reproduktion (so berichtet):**
In einem Browser, in dem man angemeldet **war** und dann abgemeldet wurde,
führt eine erneute Anmeldung nicht zum Dashboard —
man bleibt auf der Login-Seite stehen, **obwohl die Anmeldung erfolgreich war**.

Der Zusatz „obwohl eingeloggt" ist der wichtige Teil:
es ist kein fehlgeschlagener Login, sondern ein Weiterleitungsproblem *nach* dem Login.
Ein frischer Browser (oder ein privates Fenster) ist nach dem bisherigen Bild nicht betroffen —
das deutet auf Zustand, der aus der abgelaufenen Sitzung übrig bleibt.

**Was am Code gesichert ist** (gelesen, nicht ausprobiert):

1. `SecurityConfig` setzt **kein** `defaultSuccessUrl`.
   Damit gilt Spring Securitys Standard, der `SavedRequestAwareAuthenticationSuccessHandler`:
   nach dem Login wird auf den **gemerkten Request** weitergeleitet, falls einer in der Sitzung liegt,
   sonst auf `/`.
2. `unauthorized-interceptor.ts` schickt den Browser bei **jedem** 401 hart auf `/login`
   (`globalThis.location.href = …`).
3. Für `/api/**` ist ein `HttpStatusEntryPoint(401)` gesetzt.
   Das ändert nur die *Antwort* — den Request merkt sich der `ExceptionTranslationFilter`
   trotzdem, **bevor** er den Entry Point aufruft.

**Die Arbeitshypothese von gestern ist widerlegt.**
Sie lautete: der abgewiesene XHR landet als `SavedRequest` in der Sitzung,
und der Login leitet auf diese API-URL statt auf die SPA.
`LoginRedirectTest` (neu) zeigt, dass die Kette schon im ersten Glied reißt:
ein `/api/**`-Aufruf, der mit blankem 401 beantwortet wird, **legt gar keine Sitzung an** —
es gibt also nichts zu merken.
Der Test hält das fest, weil die Sache kippt, sobald jemand diesen Entry Point gegen eine
Weiterleitung tauscht: dann entsteht eine Sitzung, mit ihr der `SavedRequest`,
und der beschriebene Fehlermodus wird real, ohne dass ein Test es bemerkt.

**Was damit ausgeschlossen ist** (je ein Test in
`accountaccess/adapter/in/security/LoginRedirectTest.java`):

| Geprüft | Ergebnis |
| --- | --- |
| Login ohne Vorgeschichte | → `/` → `/app/` ✅ |
| Merkt sich der abgewiesene `/api/**`-Aufruf etwas? | **nein** — ein blankes 401 legt gar keine Sitzung an, also gibt es keinen `SavedRequest` ✅ |
| Hinterlässt der Login einen authentifizierten Kontext? | ja ✅ |
| Browser-Navigation vs. XHR auf `/app/` | 302 auf die Loginseite bzw. blankes 401 ✅ |

Ebenfalls durchgesehen und unauffällig:
`SpaController` (`/` → `/app/`, kontextpfad-relativ),
die Boot-Sequenz in `app.ts` (nichts darin leitet auf `/login`),
und der einzige Absprung im Frontend überhaupt — der 401-Interceptor.

**Der Fehler ist damit im Backend nicht nachstellbar.**
Das grenzt ein, beweist aber nichts: die Meldung hängt an echtem Browser-Zustand
(Cookies aus einer toten Sitzung, `XSRF-TOKEN`, Remember-me, Cache),
und den bildet MockMvc nicht ab.

**Spur aus dem Betrieb (2026-09-09) — und daraus ein belegter Fehler:**

```
http://domain/w2s/     -> 302  http://domain/w2s/login     <- unsere Antwort
                       -> 307  https://domain/w2s/login    <- Caddy biegt zurück
                       -> 200
[Sign in]              -> 302  /w2s/login
URL von Hand auf /w2s  -> 302  http://domain/w2s/app/      <- unsere Antwort
                       -> 307  https://domain/w2s/app/     <- Caddy biegt zurück
                       -> 200  (angemeldet)
```

Zwei Dinge stehen damit fest.
Erstens: **die Anmeldung funktioniert** — der letzte Schritt kommt ohne neuen Login ins Dashboard.
Zweitens, und das ist der eigentliche Fund: **jede absolute Weiterleitung, die wir bauen, trägt
`http://`, obwohl die Anfrage über `https` hereinkam.** Jedes `-> 307` in der Spur ist Caddy,
das unsere Antwort zurückbiegt.

**Ursache:** In der Infrastruktur terminiert Caddy TLS.
`server.servlet.context-path=/w2s` war gesetzt (`compose.yml`),
`server.forward-headers-strategy` **nicht** — Spring Boots Default ist `NONE`.
Die Anwendung hielt sich also für unverschlüsselt erreichbar
und baute jede absolute URL mit dem falschen Schema.
Spring gibt selbst nur ein relatives `Location` aus; absolut macht es erst der Servlet-Container,
und der kannte bloß den Klartext-Hop vom Proxy.

**Behoben** mit `server.forward-headers-strategy=native`, abgesichert durch
`ProxyForwardedHeadersTest` (echter Port, weil `native` ein Tomcat-Ventil ist,
das MockMvc nie erreichen würde — der Test wäre sonst leer wahr).

`native` statt `framework`, und zwar wegen der Vertrauensgrenze:
Tomcats `RemoteIpValve` wertet `X-Forwarded-*` nur aus, wenn der Peer zu
`server.tomcat.remoteip.internal-proxies` passt (private Bereiche per Default),
Springs `ForwardedHeaderFilter` glaubt jedem.
Direkt erreichbar sind wir heute nicht — `compose.yml` veröffentlicht keinen Port —,
aber „sicher, weil es zufällig so deployt ist" ist die schwächere Zusicherung als
„sicher, weil der Code prüft".

Der Preis: es **scheitert leise**.
Ein Peer außerhalb dieser Bereiche heißt, die Header werden ignoriert
und die `http`-URLs sind zurück, ohne Logeintrag.
Ein Blick auf das `Location` einer beliebigen Weiterleitung sagt nach jeder Infrastrukturänderung,
auf welcher Seite dieser Grenze man steht.
Realistischer Stolperstein: ein IPv6-fähiges Docker-Netz —
der Default kennt dort nur `::1`.

**Kontextpfad bleibt, wo er ist.**
Die Caddyfile nutzt `handle`, nicht `handle_path`, das Präfix kommt also unverändert an —
womit `server.servlet.context-path=/w2s` das passende Gegenstück ist.
Die Abhängigkeiten zwischen beiden Seiten stehen bei der Einstellung selbst
in [`compose.yml`](compose.yml), nicht hier und nicht im Code.

**Randnotiz zu den `307` in der Spur:** die stammen vermutlich gar nicht von Caddy.
Chrome zeigt HSTS-Aufwertungen als „307 Internal Redirect".
Der Browser hat unsere falschen `Location`-Angaben also stillschweigend repariert —
weshalb es überhaupt so lange unauffällig funktioniert hat.

**Was damit nicht bewiesen ist.**
Der Sprung nach `[Sign in]` auf `/w2s/login` lässt sich aus dem Code **nicht** herleiten:
Spring Securitys Erfolgs-Handler kennt nur zwei Ziele — den gemerkten Request
oder ersatzweise `/` — und keines davon ergibt die Loginseite.
Der Fix beseitigt den belegten Fehler und räumt die Kette auf;
ob er auch das gemeldete Symptom beseitigt, ist offen.

**Erledigt und im Betrieb bestätigt (2026-09-09):** der Login funktioniert wieder einwandfrei.

Damit ist auch die Zurückhaltung von gestern beantwortet.
Ich hatte geschrieben, der Fix behebe den belegten Fehler, ob er auch das Symptom beseitige,
sei offen — weil sich der Sprung auf `/w2s/login` aus Spring Securitys Erfolgs-Handler
nicht herleiten ließ.
Er ließ sich deshalb nicht herleiten, weil er dort nicht entstand:
das falsche Schema in unseren absoluten `Location`-Angaben hat die Kette gekippt,
nicht die Zielauswahl nach dem Login.
Die Lehre für das nächste Mal: bei einer Weiterleitungskette hinter einem Proxy zuerst
das **Schema jeder einzelnen Antwort** ansehen, nicht das Ziel der letzten.

- **Akzeptanzkriterium:** Eine Anmeldung nach abgelaufener Sitzung landet im Dashboard,
  in derselben Browser-Sitzung wie zuvor, ohne Cookies von Hand zu löschen.
  Kein `307` mehr in der Kette — jede unserer Weiterleitungen bleibt auf `https`.

---
