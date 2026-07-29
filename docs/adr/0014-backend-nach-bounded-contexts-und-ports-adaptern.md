# 0014. Backend nach Bounded Contexts, mit pragmatischen Ports & Adaptern

- **Date**: 2026-07-29
- **Status**: Accepted
- **Update (2026-07-29):** `shared` selbst ist in `shared/kernel/` (die fachlichen Wert-Typen
  `ImdbId`/`ReleaseYear` plus ihre Adapter: JPA-`AttributeConverter`, Spring-MVC-`Converter`) und
  `shared/platform/` (`time/`, `outbound/`, `api/`, `web/` — unverändert, nur umgezogen) unterteilt
  worden. Grund: `shared` war rein technisch nach Kategorie sortiert (organisch gewachsen, ein
  Ordner pro Schritt, sobald ein Context ihn zuerst brauchte), ohne dass ersichtlich war, *warum*
  eine Klasse dort liegt. Die Zweiteilung macht das explizit: "jeder Context braucht genau diesen
  Wert-Typ" (Kernel) vs. "nichts davon ist fachlich genug für einen eigenen Context, aber mehrere
  Contexts brauchen es" (Platform). **Kein** volles `domain/application/port/adapter` für `shared`
  — es ist kein Bounded Context, hat keinen eigenen Use-Case, und ein `port`/`adapter`-Split ohne
  etwas zu schützendes wäre reine Zeremonie. `TimeService`/`SystemTimeService` sind schon
  Port+Adapter, nur ohne die Ordner-Umbenennung — das genügt.

## Context

Das Backend war rein technisch geschichtet:
`api/` → `application/` → `services/` → `persistence/`, über einem `domain/`-Leaf, per `ArchitectureTest` (ArchUnit) erzwungen.
Diese Schichtung sagt nichts darüber, welche fachliche Fähigkeit ein Feature gehört —
eine "Watchlist" war über vier Pakete verteilt (`domain/WatchlistEntry`, `application/WatchlistImportService`,
`services/WatchlistCatalog`, `persistence/WatchlistEntryRepository`), ohne dass die Paketstruktur das erkennen ließ.
Zwei konkrete Symptome davon:

- **`PosterService`** (`application/`) und **`TitleMetaService`** (`services/`) implementieren
  unabhängig voneinander dasselbe, nicht-triviale Idiom (kurze, per Self-Proxy erzwungene
  Transaktionen um einen Cache-Read/-Write, damit keine DB-Verbindung über einen langsamen
  Netzwerk-Call gehalten wird; siehe ADR-0011) — nur weil eine Klasse zufällig als `application`
  und die andere als `services` einsortiert war, sah man beim Lesen nicht, dass beide dasselbe
  Problem lösen.
- Vier Klassen (`ImdbTitleClient`, `ImdbPosterSource`, `TmdbPosterSource`, `ImdbSuggestionClient`)
  bauen unabhängig ihren eigenen `RateLimiter`/`HttpClient` auf, obwohl sie fachlich alle zu
  "wie kommen wir an Titel-Metadaten" gehören —
  die flache Schichtung machte das nicht sichtbar, weil sie alle im selben `services/`-Paket neben
  fachlich unabhängigem Code lagen.

Zusätzlich hieß das Java-Package noch `tech.dobler.werstreamt`
(ein alter Wortwitz aus der Namensfindungsphase, "wer streamt"),
während sich der Projektname längst auf w2s / "where-to-stream" gefestigt hatte (bereits
Maven-`artifactId`/`<name>`).

## Decision

**Fachlich vor technisch schichten**: das Backend ist jetzt nach vier Bounded Contexts organisiert,
je mit einer eigenen `domain` → `application` → `port` → `adapter`-Struktur (Ports & Adapter innerhalb jedes Contexts),
plus einem bewusst minimalen `shared`-Kernel:

```
tech.dobler.where2stream/
  shared/
    kernel/                  -- ImdbId, ReleaseYear + ihre Adapter (JPA-Converter, MVC-Converter)
    platform/                -- time/, outbound/ (RateLimiter), api/ (ApiExceptionHandler), web/
  accountaccess/             -- Identität, Auth, Admin-Nutzerverwaltung, Nutzer-Preferences
  watchlist/                 -- die persönliche Liste eines Nutzers
  titlecatalog/               -- permanente Titel-Metadaten (Poster, FSK) + IMDb-Suche
  streamingavailability/      -- werstreamt.es-Scraping, TTL-Cache, Provider-Aggregation
```

Jeder Context: `domain/` (fachliche Werte + Entities), `application/` (Use-Case-Services),
`port/in/` (veröffentlichte Schnittstelle für andere Contexts, falls vorhanden),
`port/out/` (Abhängigkeiten nach außen — DB, externe APIs), `adapter/in/` (Controller),
`adapter/out/` (Implementierungen der `port/out`-Schnittstellen).

**Pragmatische Ports**, keine Zeremonie: es gibt drei Arten von "Port", aber
nur eine davon brauchte wirklich eine neue Schnittstelle.

1. **Ausgehend, zu einem externen System** (HTTP/Scraping): war schon immer eine Schnittstelle
   (`PosterSource`, `StreamAvailabilityProvider`) — unverändert, nur umgezogen.
2. **Ausgehend, zur Datenbank**: ein Spring-Data-Repository-Interface **ist** der Port (JPA liefert
   den Adapter als generierten Laufzeit-Proxy) —
   kein zusätzliches Wrapper-Interface nur der Form halber.
   Entsprechend liegen alle Repository-Interfaces jetzt unter `port/out/`, nicht unter
   `adapter/out/` (siehe "Framing A vs. B" unten).
3. **Zwischen zwei Contexts**: der einzige echte Neubau.
   Ein Context, der eine Fähigkeit für andere veröffentlicht, tut das über ein explizites
   Interface unter `port/in/` —
   z. B. `CurrentUserPort` (accountaccess, löst Username → UserId auf) oder
   `WatchlistCatalogPort` (watchlist, liest Watchlist-Fakten pro Nutzer oder global).
   Andere Contexts injizieren den *Port*-Typ, nie die konkrete Klasse.
   Für eine eigene Controller-zu-eigenem-Service-Beziehung (innerhalb desselben Context) gibt es
   dagegen bewusst kein Interface —
   das wäre reine Zeremonie.

**`port/in` vs. `port/out`, präzise**: `port/in` ist die Fähigkeit, die ein Context *veröffentlicht*
(von außen aufgerufen, so wie ein Controller seinen eigenen Service aufruft —
nur dass hier ein anderer Context der Aufrufer ist).
`port/out` ist die Abhängigkeit eines Context *nach außen* (DB, externe API) —
dafür ist "Adapter" der richtige Begriff, weil dort wirklich Infrastruktur an eine von uns
definierte Schnittstelle angepasst wird.
Bei `port/in` gibt es dagegen nichts zu adaptieren —
die Implementierung ist einfach der eigene Application-Service des Contexts
(`CurrentUserService implements CurrentUserPort`), kein separates "Adapter"-Objekt.

**Framing A vs. B (Repository-Interface = Port oder Adapter?)**: bewusst A gewählt —
das Repository-Interface selbst ist der Port, JPA der (unsichtbare) Adapter.
Das ist nicht technologie-neutral im strengen Sinn (das Interface erbt von `ListCrudRepository`,
teils mit `@Query(nativeQuery = ...)`), aber
es liefert das, was hier eigentlich gebraucht wird — Testbarkeit per Mock —, ohne für jedes
Repository ein zweites, handgeschriebenes Port-Interface plus Delegations-Adapter zu pflegen (die
"strenge" Alternative B).
`ArchitectureTest` erzwingt Framing A jetzt explizit (`spring_data_repositories_are_the_port_not_the_adapter`).

**Isolation, per ArchUnit erzwungen**: pro Context eine Regel —
kein Code außerhalb eines Context darf auf dessen Internals zugreifen, nur auf sein `port/in`.
Ausnahmen sind eng gefasst und dokumentiert:
`shared..` (der `ApiExceptionHandler` kennt bewusst jede Context-eigene Exception-Art), und die
Read-Model-Typen, die ein Port selbst zurückgibt (z. B. `ImdbEntry`, `WatchlistDate` bei
`WatchlistCatalogPort` — die sind Teil des veröffentlichten Vertrags, nicht Interna).
Eine dieser Regeln fing tatsächlich einen bestehenden, vorher unsichtbaren Architekturbruch:
`MeApiController` (accountaccess) las `TmdbProperties` (titlecatalog) direkt, um das
TMDB-Attributions-Flag in `/api/me` zu bestimmen —
behoben mit einem neuen Port (`PosterAttributionPort`), ohne die JSON-Antwort zu ändern.

**"Admin Operations" wird aufgelöst, kein eigener Context**: `CacheManagementService`,
`PreCacheService` und `RefreshService` spannten vorher Title Catalog und Streaming Availability auf —
kein eigenständiges fachliches Konzept, sondern eine Admin-Sicht über zwei andere Contexts.
Sie sind jetzt Streaming Availabilitys eigener "Cache-Maintenance"-Use-Case, der über
`WatchlistCatalogPort` und Title Catalogs `TitleCacheMaintenancePort` in die anderen Contexts
hineinreicht.
`ManageApiController`/`RefreshApiController` behalten ihre exakten URLs (die Angular Admin-UI
hängt daran) und ziehen jetzt in Streaming Availability um.

**Umbenennung**: `tech.dobler.werstreamt` → `tech.dobler.where2stream` (rein mechanisch, als
eigener erster Schritt, ~214 Dateien).
Ausdrücklich **nicht** angefasst:
`WerStreamtEsApiClient`, `WerStreamtProperties`, das `wer-streamt.*`-Property-Präfix,
`werstreamt.es`-URL-Literale, der `src/test/resources/werstreamt/`-Fixture-Ordner —
die benennen die externe, tatsächlich gescrapte Seite `werstreamt.es`, nicht unser Package.

**Migration**: inkrementell, ein Bounded Context pro Commit (Account & Access zuerst, da ihn jeder
andere Context braucht, aber er selbst niemanden;
danach Watchlist, Title Catalog, Streaming Availability), grüne Testsuite nach jedem Schritt.
Der `shared`-Kernel entstand dabei schrittweise: `TimeService`/`ApiExceptionHandler` mit dem
ersten Context, der sie brauchte;
`ImdbId`/`ReleaseYear` mit Watchlist;
`RateLimiter` erst mit Title Catalog (stellte sich als über zwei Contexts geteilt heraus,
`HttpClientFactory` dagegen als Title-Catalog-intern — beides erst beim tatsächlichen Verschieben
des Codes sichtbar, nicht vorher planbar).

## Consequences

**Einfacher / besser:**

- Fachliche Fähigkeiten sind jetzt am Package erkennbar — "Watchlist" ist ein Package, nicht vier.
- Cross-Context-Zugriffe sind explizit (ein Port-Typ im Konstruktor) statt implizit (irgendeine
  konkrete Klasse aus einem anderen Teil des `services`-Pakets).
- `ArchitectureTest` erzwingt die Grenzen jetzt aktiv —
  ein versehentlicher Zugriff auf Context-Internas schlägt beim nächsten `mvn test` fehl, nicht erst
  beim nächsten Architektur-Review.
- `PosterService`/`TitleMetaService` liegen jetzt nebeneinander in `titlecatalog/application/` —
  ihr gemeinsames Idiom ist beim Lesen sofort sichtbar, ein zukünftiges Extrahieren der
  gemeinsamen Logik ist naheliegend geworden (aber noch nicht gemacht —
  diese ADR beschreibt die Struktur, nicht die Duplikation selbst wurde in diesem Zug beseitigt).

**Schwieriger / Nachteile:**

- Tiefere Package-Pfade (`titlecatalog.adapter.out.imdb.ImdbPosterSource` statt `services.ImdbPosterSource`).
- Drei benannte Konzepte (`port.in`, `port.out`, `adapter`) statt zwei (`services`, `application`) —
  mehr zu lernen für neue Mitwirkende, auch wenn jedes Konzept einzeln klarer ist.
- Die Migration deckte zwei Fälle auf, in denen Dateien informell auf gemeinsame Pakete
  angewiesen waren (kein `import`, weil beide zufällig im selben Flat-Package lagen) —
  beim Verschieben brauchten diese Stellen einen expliziten `import`, den es vorher nicht gab.
  Kein strukturelles Problem, aber ein Migrationsschritt, der bei jedem weiteren Context
  wiederkehrte.

## Alternatives Considered

- **Flache Schichtung beibehalten, nur Namenskonvention/Dokumentation verbessern**: kostet nichts,
  löst aber das eigentliche Problem nicht —
  die F3/F8-artige Duplikation (siehe `docs/ARCHITECTURE_REVIEW.md`) entsteht gerade *weil* die
  Paketstruktur fachlich zusammengehörigen Code technisch auseinanderreißt.
- **"Admin Operations" als eigenen fünften Context**: hätte impliziert, dass Cache-Verwaltung eine
  eigene fachliche Fähigkeit ist —
  ist sie nicht, es ist eine Admin-Sicht auf zwei bestehende Contexts.
  Als Context modelliert, hätte er selbst wieder Ports zu beiden gebraucht, ohne echten Mehrwert
  gegenüber der gewählten Auflösung.
- **Strenges Ports-&-Adapter (Framing B) für jedes Repository**: jedes Repository bekäme ein
  handgeschriebenes, framework-freies Port-Interface plus einen Delegations-Adapter.
  Liefert volle Technologie-Neutralität,
  verdoppelt aber den Pflegeaufwand für eine Eigenschaft (Persistenz-Tech austauschen), die dieses
  Projekt nie nutzen wird — abgelehnt zugunsten von Framing A.
- **Eine einzige, generische ArchUnit-Slice-Regel statt vier expliziter Context-Regeln**: ArchUnits
  `slices()`-API eignet sich für Zyklus-Erkennung,
  aber nicht sauber für "außerhalb X nur über `X.port.in`" —
  vier explizite, gut kommentierte Regeln sind hier lesbarer als eine generische Konstruktion, die
  dieselbe Ausnahme-Logik nur versteckt.
