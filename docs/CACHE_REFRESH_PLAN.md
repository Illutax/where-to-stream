# Plan: „Cache Verwalten" nutzbar machen + asynchrone, gestaffelte Cache-Aktualisierung

Dieses Dokument ist der Umsetzungsplan zu [ADR-0016](adr/0016-asynchrone-verzoegerte-cache-aktualisierung.md).
Es ist bewusst so geschrieben, dass eine **andere Claude-Code-Session ohne Vorwissen aus diesem
Gespräch** direkt damit arbeiten kann: jeder Schritt nennt die konkrete Datei, die betroffenen
Methoden/Klassen und was sich ändert.

## Ausgangslage (Ist-Zustand, Stand 2026-07-30)

- `StreamInfoService.resolveAll(Collection<ImdbId>)`
  (`src/main/java/tech/dobler/where2stream/streamingavailability/application/StreamInfoService.java:87-112`)
  lädt in einer Batch-Query die aktuell gültigen (`invalidated = false`) `QueryMeta`-Zeilen; jeder
  fehlende/invalidierte/über-TTL-Treffer wird **synchron im selben Request** über
  `self.getObject()::resolve` nachgeladen (`parallelStream` parallelisiert nur die Worker-Threads,
  der Request wartet trotzdem auf das langsamste Ergebnis).
- `CatalogOverviewService.overview(userId)` (Dashboard, `GET /api/catalog`) und
  `AggregateService.getAll(userId)` (Provider-Seiten, über `ProviderPageService.pageFor(...)`,
  `GET /api/providers/{provider}`) rufen beide `resolveAll(...)` auf — beide Seiten sind also vom
  selben Blockier-/Überholungsproblem betroffen.
- `PreCacheService`
  (`.../application/PreCacheService.java`) und `CacheManagementService`
  (`.../application/CacheManagementService.java`) bilden die „Cache Verwalten"-Logik:
  `invalidate(ids)` setzt `QueryMeta.invalidated = true` (`QueryMetaRepository.invalidateByImdbIds`,
  ein `@Modifying`-UPDATE — es werden **keine** Zeilen gelöscht, nur geflaggt),
  `scrapeUncached()`/`cacheUncached()` scraped synchron alles ohne gültige (nicht-invalidierte)
  Zeile.
- `QueryMeta`-Entity (`.../domain/QueryMeta.java`): Felder `id`, `imdbId`, `creationTime` (`Instant`),
  `invalidated` (`boolean`), `queries` (`List<QueryResultDB>`). **Kein** Zeitstempel wird aktuell
  ans Frontend durchgereicht (weder in `OverviewEntryDto` noch in `ManageRowDto`).
- `wer-streamt.invalidate.after-days` (Default 28) wird als `WerStreamtProperties.Invalidate`
  (`@ConfigurationProperties`) gelesen; `StreamInfoService.isFresh(QueryMeta, Instant now)`
  (Zeile 125-132) prüft `creationTime + afterDays > now`.
- `RateLimiter` (`.../shared/platform/outbound/RateLimiter.java`) ist ein einfaches,
  Thread-blockierendes Abstandslimit (kein Bucket) — jede `WerStreamtEsSource`-Instanz hat ihre
  eigene, aber es gibt nur eine Spring-Bean-Instanz, die von allen Aufrufern (Request-Thread,
  zukünftiger Async-Refresh, zukünftiger Scheduled Job) gemeinsam genutzt wird. Das reicht bereits
  aus, um alle drei Pfade gemeinsam zu drosseln — **keine neue Rate-Limit-Logik nötig**.
- Weder `@Scheduled`/`@EnableScheduling` noch `@Async`/`@EnableAsync` werden aktuell irgendwo im
  Projekt verwendet (grep bestätigt: 0 Treffer) — beides ist Neuland für dieses Projekt.
- Frontend: `ManageTable`
  (`src/main/frontend/src/app/shared/manage-table/manage-table.ts`) zeigt nur einen binären
  Status-Pill (`manage.statusNeedsScrape` / `manage.statusCached`), keinen Zeitstempel.
  `OverviewPage`/`CatalogTable` (Dashboard) und `ProviderPage`/`FlatrateTable`/`PaidTable`
  (Provider-Seiten) haben keinerlei „veraltet"-Konzept — `OverviewEntry`/`FlatrateEntry`/`PaidEntry`
  (`core/models.ts`) tragen keinen Freshness-Flag.

## Reihenfolge / Abhängigkeiten

Phase 1 ist unabhängig und kann sofort umgesetzt werden. Phasen 2–3 gehören eng zusammen (Backend
liefert `stale`, Frontend zeigt den Banner) und sollten in einem Rutsch gehen. Phase 4 (Scheduled
Job) baut auf der in Phase 2 eingeführten `due_for_refresh_at`-Spalte auf, ist aber ansonsten
unabhängig von Phase 3 (Frontend) — kann parallel oder danach gemacht werden.

```
Phase 1 (Manage-Zeitstempel) ──────────────────────────────► fertig, unabhängig
Phase 2 (Backend: stale servieren + Async-Refresh + Spalte) ─► Voraussetzung für Phase 3 und 4
Phase 3 (Frontend: Banner)  ◄── braucht Phase 2
Phase 4 (Scheduled Job)     ◄── braucht die Migration aus Phase 2
```

---

## Phase 1 — „Cache Verwalten": Zeitstempel statt reinem Boolean

**Ziel:** Statt nur „gecacht" / „muss gescrapt werden" zeigt die Tabelle, **wann** ein Titel zuletzt
gescraped wurde (oder „nie", wenn kein Eintrag existiert).

### Backend

1. `QueryMetaRepository` (`.../port/out/QueryMetaRepository.java`): neue Batch-Methode, die die
   letzte Zeile pro `imdbId` liefert **ohne** nach `invalidated` zu filtern (ein invalidierter
   Eintrag hat ja trotzdem eine `creationTime`, die den Admin interessiert):
   ```java
   List<QueryMeta> findByImdbIdIn(Collection<ImdbId> imdbIds);
   ```
   (Spring-Data-CRUD-Methode, kein `@Query` nötig — analog zum bestehenden
   `findByImdbIdInAndInvalidatedIsFalse`.)
2. `CacheManagementService.managePage()` (Zeile 51-67): zusätzlich zu `needsScrape` pro Titel den
   spätesten `creationTime`-Wert ermitteln — batch-laden über die neue Repository-Methode und wie in
   `StreamInfoService.resolveAll` per `Collectors.groupingBy(..., Collectors.maxBy(...))` auf den
   jeweils neuesten Eintrag reduzieren (gleiches Muster, nicht neu erfinden). Ergebnis:
   `Map<ImdbId, Instant> lastScrapedAt`.
3. `ManageRowDto` (`.../application/dto/ManageRowDto.java`): Feld ergänzen:
   ```java
   public record ManageRowDto(
           ImdbId imdbId, String name, boolean isRated, boolean needsScrape,
           Instant lastScrapedAt // null = noch nie gescraped
   ) {}
   ```
4. Test: `CacheManagementServiceTest` (falls vorhanden, sonst neu) — ein Titel ohne Cache-Eintrag
   liefert `lastScrapedAt == null`; ein invalidierter Titel liefert trotzdem seinen letzten
   `creationTime`-Wert (nicht `null`) **und** `needsScrape == true` gleichzeitig — das ist der Fall,
   der aktuell unsichtbar ist und den diese Phase sichtbar macht.

### Frontend

5. `ManageRow`-Typ (`src/main/frontend/src/app/shared/manage-table/… ` bzw. wo `ManageRow` in
   `models.ts` liegt): Feld `lastScrapedAt: string | null` ergänzen (ISO-Instant-String, wie
   Jackson `Instant` serialisiert).
6. `manage-table.ts`, Status-Spalte (Zeile 76-87): Fallunterscheidung erweitern —
   - `needsScrape === true` → weiterhin Pill „muss gescrapt werden" (`manage.statusNeedsScrape`,
     unverändert, unabhängig vom Zeitstempel).
   - `needsScrape === false` → statt der generischen Pill „gecacht" den formatierten Zeitpunkt
     zeigen, z. B. über eine kleine Pipe/Helper-Funktion `formatRelative(lastScrapedAt)`
     (`transloco`-kompatibel, z. B. „vor 3 Tagen" / relative Zeit — prüfen, ob das Projekt bereits
     eine Datums-Pipe hat, sonst Angular's eingebautes `DatePipe` reicht auch als einfache erste
     Version: `{{ row.lastScrapedAt | date:'short' }}`).
   - `lastScrapedAt === null` (kann nur zusammen mit `needsScrape === true` auftreten) fällt bereits
     unter den ersten Fall ab.
7. Übersetzungs-Keys (`manage.statusCached` wird nicht mehr gebraucht, ggf. entfernen oder
   umwidmen — in den i18n-Dateien nachschauen, wo `manage.statusCached`/`manage.statusNeedsScrape`
   definiert sind, vermutlich `src/main/frontend/src/assets/i18n/*.json` oder ein Transloco-Scope
   unter `manage/`).
8. Skeleton-Loading (`SKELETON_ROWS`) unverändert lassen — ein Zeitstempel-Platzhalter ist einfach
   `skeleton-bar--narrow`, wie der bisherige Status auch schon war.

**Akzeptanzkriterium:** Auf `/app/#/manage` sieht ein ADMIN pro Titel entweder „muss gescrapt
werden" oder den Zeitpunkt des letzten Scrapes — nie mehr nur ein nichtssagendes „gecacht".

---

## Phase 2 — Backend: veraltete Daten sofort servieren, Refresh im Hintergrund

**Ziel:** `resolveAll(...)` blockiert nicht mehr auf einem bereits vorhandenen, aber
abgelaufenen/invalidierten Eintrag — es liefert die alten Werte + `stale = true` und stößt den
Refresh asynchron an. Nur ein **nie** gecachter Titel wird weiterhin synchron aufgelöst.

### 2.1 Neue Spalte `due_for_refresh_at`

9. Liquibase-Changelog ergänzen (`src/main/resources/db/changelog/`, neue Datei nach dem
   bestehenden Namensschema, z. B. `db.changelog-XX-query-meta-due-for-refresh-at.xml` — die
   nächste freie Nummer im `changelog-master`/Include ermitteln):
   ```xml
   <changeSet id="query-meta-due-for-refresh-at" author="...">
       <addColumn tableName="query_meta">
           <column name="due_for_refresh_at" type="TIMESTAMP"/>
       </addColumn>
   </changeSet>
   ```
   Nullable (bestehende Zeilen haben keinen Wert — für sie greift dann nur der `invalidated`-Zweig
   des Scheduled Jobs, siehe Phase 4; sie werden beim nächsten regulären Scrape mit einem Wert
   versorgt).
10. `QueryMeta`-Entity: Feld `dueForRefreshAt` (`Instant`, nullable) ergänzen; `QueryMeta.of(...)`
    um einen Parameter erweitern (oder eine zweite Factory-Methode `QueryMeta.of(imdbId,
    creationTime, dueForRefreshAt, queries)` — bestehende Aufrufer in `StreamInfoService.fetch(...)`
    anpassen).

### 2.2 Jitter beim Schreiben

11. `StreamInfoService.fetch(ImdbId)` (Zeile 140-147): beim Erzeugen der neuen `QueryMeta`-Zeile
    zusätzlich den fälligen Refresh-Zeitpunkt berechnen:
    ```java
    var afterDaysSeconds = TimeUnit.DAYS.toSeconds(properties.invalidate().afterDays());
    var jitterFactor = ThreadLocalRandom.current().nextDouble(1.5, 2.0); // konfigurierbar, s. 2.4
    var dueForRefreshAt = creationTime.plusSeconds((long) (afterDaysSeconds * jitterFactor));
    ```
    `ThreadLocalRandom` ist hier bewusst **kein** Verstoß gegen ADR-0003 (die `TimeService`-Facade
    betrifft nur "jetzt"/Datum, nicht Zufallszahlen) — trotzdem in eine kleine, testbare
    Hilfsmethode auslagern (z. B. `private Instant computeDueForRefreshAt(Instant creationTime)`),
    damit Tests den Jitter nicht auf `ThreadLocalRandom` selbst mocken müssen, sondern die Methode
    isoliert mit festen Grenzwerten prüfen können (z. B. „Ergebnis liegt immer zwischen
    `creationTime + 1.5×afterDays` und `creationTime + 2×afterDays`").

### 2.3 `resolveAll` liefert Stale-Info + Async-Refresh

12. Neuer Rückgabetyp statt `Map<ImdbId, List<QueryResult>>`:
    ```java
    public record ResolvedEntry(List<QueryResult> results, boolean stale) {}
    ```
    (Package `application`, neben `StreamInfoService` oder als nested record.)
13. `resolveAll(Collection<ImdbId>)` umbauen:
    - Batch laden **ohne** `AndInvalidatedIsFalse`-Filter (`QueryMetaRepository.findByImdbIdIn`,
      bereits aus Phase 1 vorhanden), pro `imdbId` die Zeile mit dem größten `creationTime` nehmen
      (wie bisher).
    - Für jeden Titel mit vorhandener Zeile:
      - `invalidated == false && isFresh(row, now)` → frisch, `stale = false`, Werte direkt
        übernehmen (unverändertes Verhalten).
      - sonst (invalidiert **oder** TTL abgelaufen) → **trotzdem** die vorhandenen `QueryResultDB`
        in `QueryResult`s wandeln (`toQueryResults`, unverändert), `stale = true` setzen, **und**
        `triggerBackgroundRefresh(imdbId)` aufrufen (siehe Punkt 15) — **kein** synchrones
        `resolve(...)` mehr für diesen Fall.
    - Für Titel **ohne** jegliche Zeile (nie gecacht): unverändertes Verhalten — synchron über
      `self.getObject()::resolve` auflösen (blockierend), `stale = false` (es gibt nichts
      "Veraltetes" zu zeigen, nur noch keine Daten).
    - Rückgabetyp wird `Map<ImdbId, ResolvedEntry>`.
14. In-Flight-Tracking gegen doppelte parallele Refreshes für denselben Titel: neue kleine Klasse
    (z. B. `shared/platform/RefreshInFlightTracker` als `@Component`, `ConcurrentHashMap.newKeySet()`
    oder `Set<ImdbId>` via `Collections.newSetFromMap(new ConcurrentHashMap<>())`), mit
    `boolean tryStart(ImdbId)` (= `set.add(imdbId)`, `true` wenn tatsächlich neu hinzugefügt) und
    `void finish(ImdbId)` (= `set.remove(imdbId)`). Diese Klasse wird sowohl vom bedarfsgetriebenen
    Pfad (hier) als auch vom Scheduled Job (Phase 4) injiziert, damit sich beide nicht gegenseitig
    doppelt triggern.
15. Neue `@Async`-Methode in `StreamInfoService` (oder einer neuen, kleinen
    `application/AsyncCacheRefreshService`, falls `StreamInfoService` dadurch zu groß wird —
    Empfehlung: eigene Klasse, um `StreamInfoService` nicht mit Executor-/Tracking-Belangen
    aufzublähen):
    ```java
    @Async("cacheRefreshExecutor")
    public void refreshInBackground(ImdbId imdbId) {
        try {
            streamInfoService.resolve(imdbId, true); // forceRefresh
        } catch (Exception e) {
            log.warn("Background refresh for {} failed", imdbId, e);
        } finally {
            tracker.finish(imdbId);
        }
    }
    ```
    **Wichtig (Spring-Fallstrick):** Diese Methode muss über den Proxy aufgerufen werden, sonst
    greift `@Async` nicht (Selbstaufruf umgeht den Proxy) — exakt dasselbe Problem, das
    `StreamInfoService` schon für `@Transactional` über das vorhandene
    `ObjectProvider<StreamInfoService> self`-Feld löst. Entweder liegt `refreshInBackground` in
    einer eigenen Klasse (dann ist das Problem automatisch gelöst, weil der Aufruf von außerhalb
    kommt), oder `StreamInfoService` ruft sich selbst konsequent über `self.getObject()` auf.
    Empfehlung: **eigene Klasse**, das ist robuster als noch mehr Selbstaufruf-Disziplin in einer
    ohnehin schon subtilen Klasse.
    Der Trigger-Aufruf aus `resolveAll` sieht dann so aus:
    ```java
    if (tracker.tryStart(imdbId)) {
        asyncCacheRefreshService.refreshInBackground(imdbId);
    }
    ```
16. Neue Konfiguration: `@EnableAsync` + Executor-Bean, z. B. neue Klasse
    `shared/platform/AsyncConfig`:
    ```java
    @Configuration
    @EnableAsync
    public class AsyncConfig {
        @Bean("cacheRefreshExecutor")
        public Executor cacheRefreshExecutor() {
            var executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(2);
            executor.setMaxPoolSize(2); // bewusst klein — der RateLimiter drosselt ohnehin auf
                                        // 2 req/s (Default), mehr Threads brächten nur Kontext-
                                        // wechsel ohne Durchsatzgewinn, siehe wer-streamt.rate-limit
            executor.setQueueCapacity(200); // deutlich mehr als eine typische Watchlist-Größe
            executor.setThreadNamePrefix("cache-refresh-");
            executor.initialize();
            return executor;
        }
    }
    ```

### 2.4 Neue Config-Properties

17. `WerStreamtProperties` (`.../adapter/out/werstreamtes/WerStreamtProperties.java`): `Invalidate`
    um Jitter-Grenzen erweitern:
    ```java
    public record Invalidate(
            @DefaultValue("28") int afterDays,
            @DefaultValue("1.5") double jitterMinFactor,
            @DefaultValue("2.0") double jitterMaxFactor
    ) {}
    ```
    Property-Namen dann `wer-streamt.invalidate.jitter-min-factor` / `-max-factor` (Kebab-Case wie
    die bestehenden Properties). In Punkt 11 statt der hartkodierten `1.5`/`2.0` diese Werte
    verwenden.

### 2.5 Anpassung der Aufrufer

18. `CatalogOverviewService.overview(userId)`: `resolveAll(...)` liefert jetzt
    `Map<ImdbId, ResolvedEntry>` — `.results()` statt der Liste direkt verwenden; zusätzlich
    `boolean hasStaleEntries = resolved.values().stream().anyMatch(ResolvedEntry::stale)` berechnen
    und ans neue Wrapper-DTO durchreichen (siehe Phase 3, Punkt 20).
19. `AggregateService.getAll(userId)` (Zeile 62-67): analog anpassen — `.results()` extrahieren,
    zusätzlich `stale`-Information nach oben durchreichen, damit `ProviderPageService`/
    `ProviderPageDto` sie in Phase 3 nutzen kann.
20. `PreCacheService` **bleibt unverändert** — `cache(...)`/`cacheUncached()`/`cacheAll()` sind die
    expliziten, weiterhin synchron-blockierenden ADMIN-Aktionen (das ist bei einem bewusst
    angestoßenen Admin-Vorgang richtig und gewollt, kein Bug).
21. `PreCacheService.findUncachedImdbIds()` bleibt fachlich unverändert (definiert weiterhin
    "kein gültiger, nicht-invalidierter Eintrag") — das ist eine andere Frage als "ist der Eintrag
    frisch genug" und wird bewusst nicht vermischt.

### Tests (Phase 2)

- `StreamInfoServiceTest`: neuer Fall — ein invalidierter/abgelaufener, aber vorhandener Eintrag
  liefert sofort (ohne auf einen Mock-Scrape zu warten) die alten Werte mit `stale = true`, und der
  Mock des Refresh-Auslösers wird **genau einmal** aufgerufen; ein zweiter gleichzeitiger Aufruf für
  denselben `imdbId` löst **keinen** zweiten Refresh aus (In-Flight-Tracker-Test).
- Ein Titel ganz ohne Cache-Eintrag liefert weiterhin synchron ein frisches Ergebnis (Regression:
  bestehendes Verhalten für "nie gecacht" darf sich nicht ändern).
- Jitter-Test: `computeDueForRefreshAt` liefert für viele Wiederholungen immer einen Wert im
  erwarteten Intervall (kein Mocking von `ThreadLocalRandom` nötig, nur Grenzwert-Assertion).

---

## Phase 3 — Frontend: „veraltet"-Banner auf Dashboard und Provider-Seiten

**Ziel:** Ein kleiner, unaufdringlicher Hinweis (kein Fehler, kein Blocker), wenn die gerade
angezeigten Titel (teilweise) veraltete Daten zeigen — **seitenweit**, nicht pro Zeile.

### Backend (DTO-Änderungen)

22. `CatalogApiController` (`GET /api/catalog`) liefert aktuell ein nacktes `List<OverviewEntryDto>`.
    Neues Wrapper-DTO:
    ```java
    public record CatalogPageDto(List<OverviewEntryDto> entries, boolean hasStaleEntries) {}
    ```
    `CatalogOverviewService.overview(...)` gibt diesen Typ zurück (Rückgabetyp-Änderung, Methode
    ggf. umbenennen falls das klarer ist, z. B. `overviewPage(...)`); Controller-Methode entsprechend
    anpassen.
23. `ProviderPageDto` (bereits ein Objekt: `{provider, included, paid}`) einfach um
    `hasStaleEntries: boolean` ergänzen — kein neuer Wrapper nötig.

### Frontend

24. `core/models.ts`: `OverviewEntry`/`FlatrateEntry`/`PaidEntry` **bleiben unverändert** (kein
    Per-Zeile-Flag, wie in ADR-0016 entschieden — YAGNI, bis explizit gebraucht). Neue/angepasste
    Wrapper-Typen:
    ```ts
    export interface CatalogPage { entries: OverviewEntry[]; hasStaleEntries: boolean; }
    export interface ProviderPage { provider: string; included: FlatrateEntry[]; paid: PaidEntry[]; hasStaleEntries: boolean; }
    ```
25. `CatalogApi.getCatalog()` (`core/api/…`): Rückgabetyp von `Observable<OverviewEntry[]>` auf
    `Observable<CatalogPage>` anpassen.
26. Neue Komponente `StaleDataBanner` (Vorlage: `ErrorAlert`,
    `src/main/frontend/src/app/shared/error-alert/error-alert.ts`, 20 Zeilen — exakt dasselbe
    Muster, andere Farbe/Text):
    ```ts
    @Component({
      selector: 'app-stale-data-banner',
      changeDetection: ChangeDetectionStrategy.OnPush,
      imports: [TranslocoPipe],
      template: `
        @if (visible()) {
          <div class="stale-data-banner" role="status">{{ 'common.staleDataBanner' | transloco }}</div>
        }
      `,
    })
    export class StaleDataBanner {
      readonly visible = input(false);
    }
    ```
    Neue Styles in `styles.scss` (neben `.error-alert`, Zeile ~167-172), z. B. mit
    `--mat-sys-tertiary-container`/`--mat-sys-on-tertiary-container`-Tokens statt der Error-Tokens
    (kein Fehler, nur ein Hinweis):
    ```scss
    .stale-data-banner {
      background: var(--mat-sys-tertiary-container);
      color: var(--mat-sys-on-tertiary-container);
      padding: 0.75rem 1rem;
      border-radius: var(--mat-sys-corner-small);
    }
    ```
27. `OverviewPage` (`features/overview/overview-page.ts`): `entries` aus `page.entries` statt
    direkt aus der Response befüllen; `<app-stale-data-banner [visible]="hasStaleEntries()" />`
    oberhalb der Tabelle/des Grids einfügen (statisches Chrome, muss **nicht** hinter dem
    `loading`-Gate stehen, siehe `CLAUDE.md`-Konvention "static chrome renders immediately" —
    allerdings ergibt der Banner erst nach dem ersten Response Sinn, also `hasStaleEntries` mit
    `false` vorbelegen bis geladen).
28. `ProviderPage` (`features/provider/provider-page.ts`): analog — Banner oberhalb von
    `FlatrateTable`/`PaidTable` einfügen, gespeist aus `ProviderPage.hasStaleEntries`.
29. Übersetzung ergänzen: `common.staleDataBanner`, z. B. deutscher Text
    „Einige Angaben werden gerade im Hintergrund aktualisiert und können kurzzeitig veraltet sein."
    (in der/den bestehenden i18n-Datei(en) neben den schon vorhandenen `manage.*`-Keys ablegen).

### Tests (Phase 3)

- Component-Test `StaleDataBanner`: rendert nichts bei `visible=false`, den Text bei `true`
  (analog zu einem etwaigen bestehenden `ErrorAlert`-Test als Vorlage).
- `OverviewPage`/`ProviderPage`: Test, dass der Banner erscheint, wenn die (gemockte) API-Antwort
  `hasStaleEntries: true` liefert, und verschwindet bei `false`.

---

## Phase 4 — Scheduled Job: proaktives, gestaffeltes Nachladen

**Ziel:** Titel, die niemand zeitnah ansieht, veralten nicht unbegrenzt — ohne bei Nichtnutzung der
App unnötige Last auf dem eigenen Server oder auf werstreamt.es zu erzeugen.

30. Neue Repository-Query in `QueryMetaRepository`, um „fällige" Titel zu finden (invalidiert ODER
    Jitter-Fälligkeit erreicht), unter den aktuell auf irgendeiner Watchlist befindlichen
    `imdbId`s:
    ```java
    @Query("""
        select q.imdbId from QueryMeta q
        where q.imdbId in :imdbIds
          and (q.invalidated = true or q.dueForRefreshAt < :now)
        """)
    List<ImdbId> findDueForBackgroundRefresh(@Param("imdbIds") Collection<ImdbId> imdbIds, @Param("now") Instant now);
    ```
    (Falls pro `imdbId` mehrere Zeilen existieren, in der Anwendungsschicht auf die jeweils
    neueste reduzieren, wie an anderer Stelle bereits üblich — oder die Query so schreiben, dass
    sie nur die aktuellste Zeile pro `imdbId` betrachtet, z. B. über eine Subquery auf
    `max(creationTime)`. Vor dem Schreiben kurz prüfen, ob es in der Praxis überhaupt mehrere
    Zeilen pro `imdbId` gibt, die beide die Bedingung erfüllen könnten — falls ja, Subquery-Variante
    verwenden, um Titel nicht mehrfach zurückzugeben.)
31. Neue Anwendungs-Service-Methode, z. B. in einer neuen Klasse
    `application/BackgroundCacheRefreshService`:
    ```java
    public int refreshDueEntries() {
        var due = queryMetaRepository.findDueForBackgroundRefresh(
                watchlistCatalogPort.allDistinctImdbIds(), timeService.now());
        if (due.isEmpty()) {
            log.debug("Background cache refresh: nothing due");
            return 0;
        }
        var started = due.stream().filter(tracker::tryStart).toList();
        started.forEach(asyncCacheRefreshService::refreshInBackground);
        log.info("Background cache refresh: {} titles queued ({} already in flight)",
                started.size(), due.size() - started.size());
        return started.size();
    }
    ```
    Nutzt denselben `RefreshInFlightTracker` und dieselbe `@Async`-Methode wie Phase 2 —
    kein separater Refresh-Mechanismus, nur ein zusätzlicher Auslöser.
32. Neue Scheduler-Klasse, z. B. `adapter/in/scheduled/CacheRefreshScheduler`
    (neues Package `adapter/in/scheduled`, analog zu `adapter/in/api`/`adapter/in/web` — ein
    dritter "Eingangs"-Adapter-Typ, der vom internen Scheduler statt von HTTP getriggert wird):
    ```java
    @Component
    @RequiredArgsConstructor
    @ConditionalOnProperty(prefix = "wer-streamt.background-refresh", name = "enabled", havingValue = "true", matchIfMissing = true)
    public class CacheRefreshScheduler {
        private final BackgroundCacheRefreshService backgroundCacheRefreshService;

        @Scheduled(cron = "${wer-streamt.background-refresh.cron:0 0 4 * * *}")
        public void refreshDueEntries() {
            backgroundCacheRefreshService.refreshDueEntries();
        }
    }
    ```
33. `@EnableScheduling` auf der Hauptanwendungsklasse (oder in `AsyncConfig` aus Phase 2, dann in
    `@EnableAsync @EnableScheduling public class AsyncConfig` zusammenfassen — Empfehlung: in
    einer gemeinsamen Konfigurationsklasse, da beides zusammengehörige "Hintergrund-Ausführung"
    ist).
34. Neue Config-Properties (`WerStreamtProperties` oder eigener Properties-Record
    `BackgroundRefreshProperties` mit Prefix `wer-streamt.background-refresh`):
    - `enabled` (Default `true`) — Not-Aus-Schalter, falls der Job in einer bestimmten Umgebung
      (z. B. Tests, Demo-Instanzen) nicht laufen soll.
    - `cron` (Default `0 0 4 * * *`, einmal täglich um 4 Uhr — bewusst ein grober Takt: der Job ist
      ein Sicherheitsnetz für Titel, die niemand ansieht, kein Ersatz für den bedarfsgetriebenen
      Pfad aus Phase 2; siehe ADR-0016 "Consequences" zum Last-Argument).

### Tests (Phase 4)

- `BackgroundCacheRefreshServiceTest`: liefert die Repository-Mock-Query 0 fällige Titel → keine
  Refresh-Aufrufe, `refreshDueEntries()` gibt `0` zurück. Liefert sie 3 Titel, von denen einer
  bereits laut `RefreshInFlightTracker` in Arbeit ist → nur 2 tatsächliche Refresh-Aufrufe.
- Kein Integrationstest, der eine echte Cron-Ausführung abwartet (zu langsam/flaky) — stattdessen
  `CacheRefreshScheduler` selbst nur mit einem trivialen Test abdecken, der prüft, dass die Methode
  den Service aufruft (die Cron-Verdrahtung ist Spring-Framework-Vertrauenssache, kein
  Eigencode-Risiko).
- `ArchitectureTest` (ArchUnit) prüfen: greift die bestehende Bounded-Context-Isolationsregel auf
  das neue `adapter/in/scheduled`-Package automatisch, oder muss sie explizit erweitert werden?
  Vor dem Schreiben kurz `ArchitectureTest` lesen (falls sie z. B. `adapter.in.(api|web)` als
  Whitelist statt als Wildcard-Pattern definiert, muss `scheduled` dort ergänzt werden).

---

## Offene Fragen für die Umsetzungs-Session

Diese Punkte sind bewusst nicht vorentschieden — beim Start von Phase 2/4 kurz klären:

1. **Cron-Takt „täglich um 4 Uhr"** ist ein Vorschlag, kein Fakt aus dem Gespräch mit dem
   Product-Owner — ggf. mit dem Nutzer (Betreiber der eigenen Instanz) abstimmen, ob das zur
   tatsächlichen Nutzungsfrequenz passt.
2. **Executor-Größe (`corePoolSize=2`)**: an die Instanzgröße/Ressourcen anpassen, falls die
   Produktivinstanz mehr Kapazität hat — aktuell bewusst konservativ (der `RateLimiter` limitiert
   ohnehin auf 2 req/s Default).
3. **Migration-Nummerierung**: die nächste freie Liquibase-Changeset-Datei/-Nummer muss zum
   Zeitpunkt der Umsetzung neu ermittelt werden (könnte sich seit diesem Plan geändert haben).
4. **`ArchitectureTest`-Anpassung** für das neue `adapter/in/scheduled`-Package (Punkt „Tests
   Phase 4") — muss vor dem ersten grünen Build geklärt sein, sonst schlägt `mvn test` fehl.
5. **i18n-Text-Feinschliff** für `common.staleDataBanner` und die angepassten `manage.*`-Keys —
   die hier vorgeschlagenen deutschen Texte sind Platzhalter, keine finale Microcopy-Abstimmung.

## Zugehörige Dokumente

- [ADR-0016](adr/0016-asynchrone-verzoegerte-cache-aktualisierung.md) — die Architekturentscheidung
  (Warum), dieses Dokument beschreibt das Wie.
- [ADR-0012](adr/0012-permanenter-titel-cache-vs-ttl-verfuegbarkeits-cache.md) — die bestehende
  Caching-Strategie, die dieser Plan erweitert (nicht ersetzt).
- [`TODOs.md`](../TODOs.md) — Backlog-Einträge TODO-43 bis TODO-46, je einer pro Phase.
