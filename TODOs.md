# TODOs — offene Arbeit

**Hier steht nur, was noch zu tun ist.** Erledigtes wandert nach [`DONE.md`](DONE.md).

Diese Trennung ist keine Kosmetik, sie ist der Grund, warum diese Datei stimmen kann.
Solange Erledigtes hier lag, waren 86 % der Zeilen Historie im Präsens —
und man konnte einem Eintrag nicht ansehen, ob ein alter Klassenname bloß alt
oder die Aussage falsch geworden war. Jetzt gilt: **was hier steht, gilt jetzt.**

Priorität: 🔴 hoch · 🟠 mittel · 🟡 mittel-niedrig · 🟢 niedrig

## Regeln für Einträge

1. **Zeigen statt wiederholen.** Verlinke die maßgebliche Stelle (ADR, Property, Klasse),
   statt ihren Inhalt abzuschreiben. Jede Kopie einer Tatsache driftet für sich —
   `ddl-auto` stand einmal an drei Stellen, zwei davon falsch.
2. **Belege mit Pfad**, nicht mit „irgendwo im Service" — so wie TODO-22 auf
   `src/main/java/tech/dobler/where2stream/watchlist/application/ExportReader.java` zeigt.
   `DocumentationConsistencyTest` prüft beim Build, dass es die Datei gibt;
   ein Pfad in Backticks, den es nicht gibt, macht den Build rot.
3. **Ungeprüftes als ungeprüft kennzeichnen.** „Nicht verifiziert:" ist eine vollwertige
   Aussage; eine Vermutung, die wie ein Befund aussieht, kostet später mehr als sie spart.
4. **Beim Erledigen:** Eintrag nach [`DONE.md`](DONE.md) verschieben, nicht hier abhaken.
   Ist die Begründung dauerhaft wertvoll, gehört sie vorher in eine ADR — das Archiv
   wird nicht gepflegt.

Der Ablauf im Detail steht als Skill unter
[`.claude/skills/ticket/SKILL.md`](.claude/skills/ticket/SKILL.md).

---

## Übersicht

| | Ticket | Kurz |
| --- | --- | --- |
| 🔴 | [TODO-54](#todo-54) | Node-/npm-Version an einer Stelle verbindlich festlegen |
| 🔴 | [TODO-63](#todo-63) | `W2S_ADMIN_PASSWORD` aus `.env` bindet an keine Property |
| 🟠 | [TODO-62](#todo-62) | README gegen den Code abgleichen |
| 🟠 | [TODO-65](#todo-65) | Neues Architecture Review als datierte Momentaufnahme |
| 🟡 | [TODO-59](#todo-59) | `/api/titles/{id}/meta`: ein Request pro Zeile, unstorniert |
| 🟢 | [TODO-22](#todo-22) | Hartkodiertes CSV-Header-Array |
| 🟢 | [TODO-42](#todo-42) | Keine Mindestlänge/Komplexität für Passwörter |
| 🟢 | [TODO-52](#todo-52) | Angular-Bundle-Größe reduzieren (Trigger: 1 MB Initial-Bundle) |
| 🟢 | [TODO-60](#todo-60) | `PaidEntryDto.year` als Zahl ausliefern |
| 🟢 | [TODO-64](#todo-64) | Kleine Aufräumfunde aus der TODO-Prüfung |


## 🔴 Hoch

### 🔴 TODO-54 — Node-/npm-Version an einer Stelle verbindlich festlegen
Die zulässige Toolchain steht heute an **vier** Orten, die getrennt gepflegt werden und bereits
auseinanderlaufen:

| Ort | Aussage | Stand 2026-09-06 |
| --- | --- | --- |
| `src/main/frontend/.nvmrc` | `24` | nur Major |
| `src/main/frontend/package.json` → `engines` | `node >=22 <25`, `npm >=10` | Spanne |
| `src/main/frontend/package.json` → `packageManager` | `npm@11.16.0` | **exakt, und veraltet** |
| `Dockerfile` → `NODE_BASE_IMAGE` | `node:24-alpine` | Major, Minor/Patch fließend |

`src/main/frontend/.npmrc` setzt `engine-strict=true` — eine Toolchain außerhalb der Spanne bricht
`npm ci` also **hart** ab, was richtig ist, aber bedeutet: jede Abweichung legt den Build still.

**Was aktuell gilt** (gegen die Registry geprüft, nicht geschätzt):

- Angular 22.0.7 verlangt `node ^22.22.3 || ^24.15.0 || >=26.0.0` — **Node 25 ist ausdrücklich
  ausgenommen**, die Spanne springt von 24 auf 26.
- `npm` steht bei **12.0.2**; der letzte 11er ist 11.19.1. Das in `packageManager` gepinnte
  11.16.0 gibt es also weder in der Node-24-Zeile noch sonstwo als aktuelle Version.
- Angular selbst ist bei 22.1.5, das Projekt bei 22.0.7 — ein Minor-Rückstand, kein Problem.

- **Akzeptanzkriterium:** Eine Quelle der Wahrheit für Node und npm, aus der die anderen Orte
  abgeleitet oder gegen die sie geprüft werden. Mindestens: `packageManager` entweder pflegen oder
  entfernen, und `NODE_BASE_IMAGE` auf dieselbe Spanne festnageln wie `engines`.
- **Zu entscheiden:** ob `engines` auf `>=22 <25` bleibt (dann muss jede Node-Aktualisierung auf
  25 bewusst blockiert werden) oder auf Angulars eigene Spanne umgestellt wird
  (`^22.22.3 || ^24.15.0 || >=26.0.0`), die die 25er-Lücke korrekt abbildet.
- **Hängt zusammen mit TODO-55:** die Versionsfrage wurde erst dadurch akut, dass der
  Auto-Upgrade-Lauf sie ungebremst trifft.

---

## eBay-Rückbau und Ersatz (2026-09-06)

### 🔴 TODO-63 — `W2S_ADMIN_PASSWORD` aus `.env` bindet an keine Property
`.env.example:11` dokumentiert `W2S_ADMIN_PASSWORD` als das initiale Admin-Passwort.
Es bindet an nichts.

`compose.yml` mappt nur noch `W2S_SECURITY_INITIALADMIN_USERNAME` (Zeile 27);
die zugehörige Passwort-Zeile wurde entfernt, als `env_file: .env` hinzukam.
Über `env_file` landet `W2S_ADMIN_PASSWORD` zwar im Container, aber der Property-Prefix ist
`w2s.security` — ein `w2s.admin.password` gibt es nicht.

**Folge:** Wer das dokumentierte Passwort setzt, bekommt es nicht.
`AdminUserSeeder` hält den Wert für leer, erzeugt ein zufälliges Passwort und **loggt es**.
Das fällt niemandem auf, der nicht ins Log sieht — man probiert das Passwort aus `.env`,
es geht nicht, und die naheliegende Vermutung ist ein Tippfehler beim Anlegen.

**Der Kontrast, der den Befund stützt:** `TMDB_API_KEY` flog im selben Commit aus `compose.yml`
und funktioniert über `env_file` trotzdem — weil `tmdb.api-key` relaxed-binding-fähig ist.
Beim Admin-Passwort passt der Name nicht.

- **Akzeptanzkriterium:** Entweder die Zeile in `compose.yml` wiederherstellen, oder
  `.env.example` auf den bindungsfähigen Namen umstellen.
  Danach einmal mit gesetztem Passwort hochfahren und prüfen, dass der Seeder **kein**
  generiertes Passwort loggt — das ist die Probe, die den Fehler von Anfang an gezeigt hätte.
- **Ungeprüft:** `README.md:212` nennt `W2S_SECURITY_INITIAL_ADMIN_PASSWORD`, `compose.yml:26`
  besteht auf `…INITIALADMIN_…`. Beide binden vermutlich über Spring Boots
  Underscore-Mapping — verifiziert ist das nicht, und die beiden Aussagen widersprechen sich.


## 🟠 Mittel

### 🟠 TODO-62 — README gegen den Code abgleichen
Die README ist an vielen Stellen vom Code überholt.
Beim Prüfen der TODOs fielen unabhängig voneinander diese Punkte auf:

| Stelle | Steht dort | Tatsächlich |
| --- | --- | --- |
| `:12` | „Spring Boot 4.1" | 4.2.0-M1 |
| `:37` | `WerStreamtEsApiClient` | `WerStreamtEsSource` |
| `:132` | „`ImdbApiClientTest` ist per Default ausgeschlossen" | Test und Klasse gibt es seit TODO-1 nicht mehr; kein solcher Ausschluss in `pom.xml` |
| `:201` | `curl … /check-pre-cache` | Endpunkt entfällt; heute `/api/cache/uncached` |
| `:284` | „Cache füllt sich via `/pre-cache`" | heute `POST /api/cache` |
| `:264`/`:267` | Rate-Limit-Defaults `2` | `src/main/resources/application.properties` setzt `20` bzw. `10`; die `2` ist nur der Code-Fallback |
| `:298` | „`mvn verify` startet einen Container" | die Tests hängen an Surefire, laufen also schon bei `mvn test` |
| Endpunkt-Tabelle | — | fehlen u. a. `/api/imdb/…`, `/api/titles/{id}/meta`, die `PUT /api/me/*`, Impersonierung |
| Feature-Liste | — | weder eBay-Suchlink (TODO-57) noch Admin-Impersonierung (TODO-53) erwähnt |

Nicht in der README, aber deploymentkritisch und nirgends dokumentiert:
`server.forward-headers-strategy=native` und `server.servlet.context-path=/w2s`
— beide mit **leisem** Fehlverhalten, wenn sie nicht stimmen (TODO-61).

Auch `http-clients/testing.http` zeigt noch auf die gelöschten `/pre-cache`-Endpunkte.

- **Akzeptanzkriterium:** Die Tabellen und Beispiele der README treffen den Code.
  Sinnvoll wäre, dabei zu überlegen, was sich **automatisch** prüfen lässt —
  eine Endpunkt-Tabelle von Hand zu pflegen driftet zuverlässig wieder ab.


### 🟠 TODO-65 — Neues Architecture Review als datierte Momentaufnahme
Der Vorgänger ([`docs/reviews/2026-07-28-architecture-review.md`](docs/reviews/2026-07-28-architecture-review.md))
ist datiert auf den Tag **vor** [ADR-0014](docs/adr/0014-backend-nach-bounded-contexts-und-ports-adaptern.md).
Er hat den Umbau ausgelöst, der ihn überholt hat — seither ist kein Nachfolger entstanden.

**Die Form ist wichtiger als der Turnus.**
Ein Review ist eine **Momentaufnahme mit Datum im Namen**, kein lebendes Dokument.
Genau daran ist der Vorgänger gescheitert: er stand undatiert unter `docs/` und wurde
gelesen, als beschriebe er den heutigen Stand.
Ein Stand, der sein Datum trägt, darf altern.

- **Ablage:** **docs/reviews/JJJJ-MM-TT-architecture-review.md**, nach dem Schreiben
  **nicht mehr geändert** (Tippfehler ausgenommen).
- **Ergebnis sind Handlungen, nicht Prosa:** Was dauerhaft gilt, wird eine **ADR**;
  was zu tun ist, wird ein **TODO**. Das Reviewdokument selbst begründet nur den Befund.
  Ohne diese Regel entsteht ein drittes Dokument, das mit den anderen beiden auseinanderläuft.
- **Umfang:** die vier Bounded Contexts und ihre Grenzen, `shared`, der Frontend-Aufbau,
  die ArchUnit-Regeln (decken sie noch ab, was sie sollen?),
  und ausdrücklich die Frage, welche der 20 ADRs die Realität **nicht** mehr beschreiben.
- **Ausführen erst nach TODO-62 und TODO-64** — sonst prüft das Review Doku,
  von der wir schon wissen, dass sie falsch ist.
- **Wiederholbar:** die Durchführung liegt als Skill unter
  [`.claude/skills/architecture-review/`](.claude/skills/architecture-review/SKILL.md),
  damit der nächste Durchlauf nicht wieder neu erfunden wird.

- **Akzeptanzkriterium:** Ein datiertes Dokument unter `docs/reviews/`, das den Ist-Zustand
  beschreibt; jeder Handlungsbedarf daraus als TODO oder ADR erfasst,
  nicht als offene Liste im Reviewdokument.

## 🟡 Mittel-niedrig

### 🟡 TODO-59 — `/api/titles/{id}/meta`: ein Request pro Zeile, unstorniert
Beim Review von TODO-57 gemessen (nicht geschätzt), Aufbau mit 300 Kacheln:
`injectTitleMeta` (`src/main/frontend/src/app/core/title-meta.ts`) feuert **einen GET pro Zeile**,
sobald Altersfreigaben **oder** deutsche Titel eingeschaltet sind —
Altersfreigaben sind per Default an, also ist das der Normalfall.

Zwei getrennte Probleme:

1. **Keine Stornierung.** Die `subscribe()` im `effect()` hängt an keinem Destroy-Hook.
   Nach `fixture.destroy()` waren **0 von 300** Requests storniert.
   Der View-Umschalter auf dem Dashboard (`@if (viewMode() === 'GRID')`) zerstört alle Zeilen
   und baut sie neu auf — einmal hin und her sind 600 Requests, 300 davon verwaist.
2. **Kein Dedup, kein Batch.** Jede Zeile fragt einzeln, ohne Client-Cache.
   Über HTTP/1.1 ergibt das eine Sechserschlange mit Head-of-Line-Blocking.

**Vorbestehend, nicht durch TODO-57 verursacht** — der Suchlink liest das Signal nur mit
und löst nichts zusätzlich aus (nachgemessen).
Aufgenommen, weil der Befund sonst mit dem Review verloren geht.

**Zu tun:**
- `takeUntilDestroyed()` / `DestroyRef` in `injectTitleMeta` — behebt Punkt 1 allein.
- Für Punkt 2 ein Sammelendpunkt `/api/titles/meta?ids=…`, den die Seite einmal ruft.

**Punkt 2 ist teilweise erledigt (2026-09-07):** `TitleMetaApi` hält jetzt ein geteiltes Signal
je `ImdbId` plus eine In-Flight-Sperre, weil die neue eBay-Spalte sonst einen **zweiten** Abruf
je Zeile ausgelöst hätte — die Spalte hätte sich in Traffic selbst bezahlt.
Damit kostet ein Titel einen Request, egal wie viele Komponenten ihn zeigen,
und ein Ansichtswechsel fragt nichts erneut ab.
**Offen bleibt:** n Zeilen sind weiterhin n Requests (dafür braucht es den Sammelendpunkt),
und storniert wird immer noch nichts — Punkt 1 ist unangetastet.

- **Akzeptanzkriterium:** Ein Wechsel der Ansicht hinterlässt keine offenen Requests;
  ein Dashboard mit n Zeilen erzeugt nicht mehr n Metadaten-Requests.


## 🟢 Niedrig

### 🟢 TODO-22 — Hartkodiertes CSV-Header-Array
`watchlist/application/ExportReader.headers`: 18 feste Spaltennamen, und die **echte** Kopfzeile
der Datei wird per `setSkipHeaderRecord(true)` verworfen — die Zuordnung ist rein **positionell**.

**Der Schaden ist größer als „bricht still" vermuten lässt.**
Ein komplett fremdes Format scheitert laut: alle Zeilen fallen durch,
`WatchlistImportService` wirft `InvalidImportException`.
Gefährlich ist der Zwischenfall — IMDb fügt **eine** Spalte ein oder sortiert um.
Dann liest `record.get("Title")` still das falsche Feld, Zeilen mit einem gültigen `tt…`-Link
laufen durch, und weil der Import ein **Full-Sync** ist, werden Bestandseinträge gelöscht,
die in der fehlinterpretierten Datei scheinbar fehlen.

- **Akzeptanzkriterium:** Header aus der Datei lesen
  (`CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)` — commons-csv 1.10)
  und die fünf tatsächlich benötigten Spalten (`Created`, `Title`, `Year`, `Your Rating`, `URL`)
  **einmalig** gegen den gelesenen Header prüfen, mit sprechender Fehlermeldung.
  Sonst bekommt der Nutzer nur das generische „No valid entries found".

---

## Architektur-Review (2026-07-28)

Vollständige Analyse: [`docs/reviews/2026-07-28-architecture-review.md`](docs/reviews/2026-07-28-architecture-review.md). Die meisten
Funde wurden direkt umgesetzt (siehe Commit-Historie); ein Punkt wird hier stattdessen als
Low-Prio-Ticket für später vorgemerkt, statt sofort umgesetzt zu werden.

### 🟢 TODO-42 — Keine Mindestlänge/Komplexität für Passwörter
`CreateUserCommand`/`ResetPasswordCommand` prüfen im Compact Constructor nur auf nicht-blank,
keine Mindestlänge oder Komplexität — ein ADMIN kann einem Account ein Ein-Zeichen-Passwort geben.
(Die Validierung lag früher in `UserAdminService`; der Service kodiert heute nur noch.)
Ebenso keine Prüfung für das initiale Admin-Passwort (`w2s.security.initial-admin.password`).
- **Akzeptanzkriterium:** Sinnvolle Mindestanforderungen (Länge, ggf. Zeichenklassen) einführen,
  serverseitig durchsetzen, Fehlermeldung im Frontend anzeigen.
- **Der Platzhalter selbst ist kein Problem:** `W2S_ADMIN_PASSWORD=change-me-please` in
  `.env.example` folgt der etablierten „bitte ändern"-Konvention dieser Datei
  (vgl. `MARIADB_ROOT_PASSWORD=change-me` direkt darunter).
- ⚠️ **Der zweite Halbsatz dieses Absatzes war falsch und ist entfernt.** Er behauptete,
  `compose.yml` übersetze die `.env`-Namen korrekt auf die Spring-Properties.
  Für das **Passwort** trifft das nicht (mehr) zu — nachgeprüft: `compose.yml` mappt nur noch
  `W2S_SECURITY_INITIALADMIN_USERNAME`, die Passwort-Zeile fehlt.
  Als eigener Bug herausgezogen: **TODO-63**.

---

## eBay-Preisabfrage (2026-09-05)

### 🟢 TODO-52 — Angular-Bundle-Größe reduzieren (Trigger: 1 MB Initial-Bundle)
**Nicht jetzt angehen.** Das Initial-Bundle liegt bei **657,75 kB roh / 146,74 kB** geschätzt
komprimiert (gemessen 2026-09-09). Das ist bewusst akzeptiert; dieses Ticket sammelt die
gemessenen Hebel für den Tag, an dem es eng wird.

**Bemerkenswert:** Der Rückbau der eBay-Preisabfrage (TODO-56) hat das Initial-Bundle **nicht**
verkleinert — vorher 655,85 kB, danach 657,75 kB. Der entfallene Code lag vollständig in
Lazy-Chunks; der neue Suchlink kostet dort ein paar hundert Byte mehr, als das alte Widget
gekostet hat. Wer beim Aufräumen auf eine Ersparnis im Initial-Bundle hofft, sucht an der
falschen Stelle: dort liegen Angular, Material und Transloco, nicht unsere Features.

**Der Trigger liegt im Code, nicht in diesem Text:** `src/main/frontend/angular.json` bricht den Build ab, sobald das
Initial-Bundle **1 MB** erreicht (`budgets[type=initial].maximumError`), mit einer Vorwarnung ab
950 kB. Wer diesen Abbruch sieht, landet über den Kommentar dort bei diesem Ticket.

**Messung vom 2026-09-05** (esbuild-Metafile, `ng build --stats-json`):

| Anteil an `main.js` | Paket |
| --- | --- |
| 147,0 kB (24 %) | `@angular/core` |
| 131,0 kB (21 %) | `@angular/material` |
| 107,2 kB (18 %) | `@angular/cdk` |
| 76,5 kB (12 %) | `@angular/router` |
| **23,8 kB (3,9 %)** | **eigener Anwendungscode** |

Der wichtigste Befund zuerst: **unser eigener Code macht 3,9 % aus.** Optimierung daran ist per
Konstruktion wirkungslos. Der Hebel liegt allein darin, welche Framework-Fläche im *Initial*-Chunk
landet.

**Vier Hebel, in dieser Reihenfolge:**

1. **Zuerst die Metrik prüfen, nicht den Code.** Das Budget steht auf der Rohgröße; Nutzer laden die
   komprimierte. Bevor jemand Bytes jagt, ist zu entscheiden, welche Zahl wir eigentlich verwalten
   wollen — sonst optimiert man gegen die falsche.
2. **Suchbox/Dialog aus der App-Hülle lösen — der große Hebel.**
   `src/main/frontend/src/app/app.ts` lädt `ImdbSearchBox` eager; die injiziert `MatDialog` und zieht damit
   `material/dialog`, `cdk/dialog` **und** `cdk/overlay` in den Initial-Chunk — für einen Dialog,
   der erst aufgeht, nachdem jemand getippt *und* ein Ergebnis angeklickt hat.
   **Gemessen** durch probeweises Entfernen und Neubauen: **−93,98 kB roh / −18,45 kB komprimiert**,
   also 15 % von `main.js`; Overlay und Dialog verlassen den Initial-Chunk vollständig.
   Umsetzung: `@defer (on interaction)` um die Suchbox, oder das Dialog-Öffnen in einen dynamisch
   importierten Teil ziehen.
   **Preis:** die Suchbox sitzt sichtbar in der Toolbar, `on interaction` bedeutet eine kleine
   Verzögerung beim ersten Klick ins Suchfeld. Bewusste UX-Entscheidung, keine reine Verbesserung.
3. **Font-Subsets auf `latin`/`latin-ext` beschränken.**
   `src/main/frontend/angular.json` bindet `@fontsource/roboto/{400,500,700}.css` ein — **alle** Subsets. Ausgeliefert
   werden 768 KB Schriften: cyrillic (165 kB), math (115 kB), greek (65 kB), symbols (57 kB),
   vietnamese (43 kB) — von einer DE/EN-Oberfläche nie gebraucht. Nutzer laden sie dank
   `unicode-range` zwar nicht herunter, aber sie liegen im Deployment und im Image. Zudem sind
   **54 % des Initial-Stylesheets** `@font-face`-Regeln (14,8 kB von 26,9 kB), davon nur 2,3 kB
   latin/latin-ext. Erwartet: ~12 kB weniger Initial-CSS, ~440 kB kleineres Artefakt.
4. **Danach neu messen** und, falls immer noch zu groß, die Grenze bewusst anheben statt sie zu
   umgehen.

**Was hier ausdrücklich nicht die Antwort ist:** Angular Material gegen handgeschriebene Komponenten
tauschen (238 kB gegen eine dauerhafte Wartungs- und Barrierefreiheitsschuld), oder weiter
zerschneiden, nur um eine Zahl zu treffen.

---

---

## Build-Toolchain (2026-09-06)

### 🟢 TODO-60 — `PaidEntryDto.year` als Zahl ausliefern
`PaidEntryDto` (`streamingavailability/application/dto`) formatiert das Jahr auf dem Server
(`imdbEntry.year().display()`), liefert also `"Not yet released"` als Text.
Zwei Folgen, beide beim Review von TODO-57 aufgefallen:

- **Der Client kann damit nicht rechnen.** `TileEntry.releaseYear` ist nur deshalb nullable —
  aus einem fertigen String lässt sich kein Jahr zurückgewinnen, ohne zu raten.
  Der eBay-Suchlink ist der erste Fall, der daran hängt, vermutlich nicht der letzte.
- **Der Text ist unübersetzt englisch** und landet so in einer zweisprachigen Oberfläche,
  während der Client dieselbe Konstante in `src/main/frontend/src/app/core/domain.ts` ohnehin führt.

`OverviewEntryDto` und `FlatrateEntryDto` machen es bereits richtig und liefern `ReleaseYear`.

- **Akzeptanzkriterium:** `PaidEntryDto.year` ist eine Zahl,
  die Formatierung liegt im Client bei `releaseYearDisplay`,
  und `TileEntry.releaseYear` ist nicht mehr nullable.

---

## Bestandsaufnahme aller TODOs (2026-09-09)

Alle 61 Einträge wurden gegen den Code geprüft — die aus dieser Sitzung von mir selbst,
die übrigen 46 in Viererbündeln von Subagenten.
Auslöser war ein Sachfehler, den der Auftraggeber beim Durchsehen der letzten 15 Commits fand:
die Dokumentation behauptete durchgängig, der eBay-Developer-Account sei nie freigeschaltet worden.
Er war es; das Feature lief und wurde bewusst zurückgebaut (siehe TODO-56).
Die drei Einträge unten sind das, was die Prüfung an **neuer** Arbeit zutage gefördert hat.

### 🟢 TODO-64 — Kleine Aufräumfunde aus der TODO-Prüfung
Einzeln zu klein für ein Ticket, zusammen eine Stunde Pfadfinderarbeit:

- **Toter Code:** `QueryMetaRepository.findByImdbIdInAndInvalidatedIsFalse(...)` hat keinen
  Aufrufer mehr — verdrängt von `findByImdbIdIn(...)`.
- **Verwaistes Javadoc:** `PreCacheService` behauptet, auch der „per-import targeted pre-cache"
  nutze den Service — `WatchlistImportService` injiziert ihn gar nicht.
  `CatalogApiController` beschreibt sich als „die Daten hinter der Thymeleaf-`index`-Seite";
  Thymeleaf ist mit ADR-0008 entfallen.
- **`docs/reviews/2026-07-28-architecture-review.md` widerspricht dem Code:** behauptet, `AggregateService` existiere
  nicht mehr (existiert), und beschreibt die Schichtung als `api/ → application/ → services/ →
  persistence/` (Stand vor ADR-0014).
- **Überflüssige Imports** in `StatusController` (importiert aus dem eigenen Paket).
- **Nicht erzwungen:** dass in `adapter/in` kein `@Transactional` steht, hält heute — es gibt aber
  keine ArchUnit-Regel dafür. Eine Regel wäre billiger als der nächste Rückfall.
