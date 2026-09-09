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
| 🟠 | [TODO-65](#todo-65) | Neues Architecture Review als datierte Momentaufnahme |
| 🟠 | [TODO-66](#todo-66) | resilience4j zurückholen und auf die Outbound-Adapter anwenden |
| 🟠 | [TODO-67](#todo-67) | ADRs auf Aktualität prüfen |
| 🟡 | [TODO-59](#todo-59) | `/api/titles/{id}/meta`: ein Request pro Zeile, unstorniert |
| 🟢 | [TODO-22](#todo-22) | Hartkodiertes CSV-Header-Array |
| 🟢 | [TODO-42](#todo-42) | Keine Mindestlänge/Komplexität für Passwörter |
| 🟢 | [TODO-52](#todo-52) | Angular-Bundle-Größe reduzieren (Trigger: 1 MB Initial-Bundle) |
| 🟢 | [TODO-60](#todo-60) | `PaidEntryDto.year` als Zahl ausliefern |


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


## 🟠 Mittel


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

### 🟠 TODO-66 — resilience4j zurückholen und auf die Outbound-Adapter anwenden
Mit dem Rückbau von `purchaseoffers` (TODO-56) fiel der einzige Nutzer von
`resilience4j-spring-boot4` weg und die Abhängigkeit mit ihm.
Die Anwendung hat seither **keinen** Circuit Breaker mehr.

**Der Bedarf ist damit nicht verschwunden**, nur der Nutzer.
Drei Adapter sprechen mit fremden Diensten, die real ausfallen:

| Adapter | Fremddienst | Heute |
| --- | --- | --- |
| `src/main/java/tech/dobler/where2stream/streamingavailability/adapter/out/werstreamtes/WerStreamtEsSource.java` | werstreamt.es (Scraping) | `try/catch` je Aufruf, `RateLimiter` |
| `src/main/java/tech/dobler/where2stream/titlecatalog/adapter/out/imdb/ImdbTitleSource.java` | IMDb | `try/catch` je Aufruf |
| `src/main/java/tech/dobler/where2stream/titlecatalog/adapter/out/tmdb/TmdbPosterSource.java` | TMDB | `try/catch` je Aufruf |

Ein `try/catch` fängt den einzelnen Fehlschlag ab, aber es **hört nicht auf zu fragen**.
Bei einem länger ausgefallenen Dienst läuft jeder Aufruf erneut in den Timeout —
und `PreCacheService` und `RefreshService` fächern über `parallelStream` auf,
der Hintergrund-Job ebenso.
Genau dafür gibt es den Breaker: nach einer Fehlerrate kurzschließen und es
nach einer Weile mit zwei Probeaufrufen erneut versuchen.

**Was aus dem alten Anlauf übernommen werden kann** (steht ausführlich in TODO-51 in
[`DONE.md`](DONE.md)): das Boot-4-Artefakt heißt **`2.4.0`**, nicht `2.3.0`;
die Konfiguration gehört nach Java und nicht in Properties, weil
`src/test/resources/application.properties` die Produktionsdatei im Testklassenpfad
überschattet und ein Tippfehler in einem Klassennamen-String still auf die Defaults zurückfällt.

**Zu entscheiden:** eine Breaker-Instanz je Dienst (drei) oder eine gemeinsame.
Getrennt, würde ich meinen — ein ausgefallenes TMDB soll die Verfügbarkeitssuche nicht mitreißen.

- **Akzeptanzkriterium:** Jeder der drei Adapter ist mit einem eigenen Breaker versehen,
  die Konfiguration liegt in Java, und ein Test belegt je Adapter, dass der Breaker bei
  anhaltenden Fehlern öffnet — und dass ein geöffneter Breaker die Seite **nicht** kaputt macht,
  sondern in denselben Zustand mündet wie ein einzelner Fehlschlag heute.

### 🟠 TODO-67 — ADRs auf Aktualität prüfen
20 ADRs, davon 19 `Accepted` und eine `Superseded`.
Geprüft wurde zuletzt keine — und dass drei von ihnen bis zum 2026-09-09 auf `Proposed` standen,
obwohl sie längst liefen, zeigt, dass der Status niemandem auffällt.

**Eine ADR, die niemand mehr befolgt, ist schlimmer als keine** — sie sieht aus wie eine
Zusicherung, auf die man sich verlassen kann. Beim Prüfen der TODOs sind zwei Fälle
aufgefallen, die genau in diese Richtung deuten:

- [ADR-0011](docs/adr/0011-kein-open-session-in-view.md) („kein OSIV, alles EAGER") ist gültig —
  aber TODO-12 forderte jahrelang das Gegenteil, ohne dass der Widerspruch auffiel.
- [ADR-0019](docs/adr/0019-port-spi-fuer-umgekehrte-kontextabhaengigkeiten.md) hat mit dem
  eBay-Rückbau einen ihrer beiden Anwendungsfälle verloren. Sie gilt weiter, steht jetzt aber
  auf einem einzigen Bein.

**Je ADR drei Fragen:** Beschreibt sie die Realität? Wird sie befolgt — nachweisbar, nicht
dem Anschein nach? Ist ihre Begründung noch die, die heute zählen würde?

- **Ergebnis:** Status nachziehen (`Superseded`, wenn überholt) und die Verweise darauf mit.
  Eine ADR, die stillschweigend gebrochen wird, ist **kein** Doku-Problem — dann ist entweder
  der Code oder die Entscheidung falsch, und beides gehört als eigenes Ticket erfasst.
- **Abgrenzung zu TODO-65:** Das Architecture Review prüft den Code gegen sich selbst,
  dieses Ticket die Entscheidungen gegen den Code. Sinnvoll zusammen zu machen —
  der `architecture-review`-Skill führt den ADR-Abgleich als eigenen Bereich.

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

