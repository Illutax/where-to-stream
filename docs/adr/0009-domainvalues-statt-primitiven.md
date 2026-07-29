# 0009. Domain-Values statt Primitiver Datentypen

- **Date**: 2026-07-25
- **Status**: Accepted

## Context

Domänenkonzepte wurden als nackte Primitive durch den ganzen Stack gereicht:
die IMDb-Id als `String`, das Erscheinungsjahr als `int`, das Hinzufüge-Datum als `String`.
Das hat mehrere Nachteile:

- **Verstreute Validierung / Regeln.** Das `tt\w+`-Format der IMDb-Id wurde nur im `ExportReader`
  geprüft;
  die „Jahr 0 = noch nicht erschienen"-Regel lag verteilt in den DTO-Factories (`PaidEntryDto`).
- **Vertauschbarkeit.** Nichts hinderte daran, einen beliebigen `String` (z. B. einen Service-Namen)
  dort zu übergeben, wo eine IMDb-Id erwartet wird.
- **Kein Ort für Domänenlogik.** Kanonische URL, Jahres-Anzeige, Datums-Vergleich lagen als
  Ad-hoc-Ausdrücke in Controllern/Templates.

## Decision

Für die drei Konzepte werden **Value Objects** eingeführt — im Backend **und** im Angular-Client —,
ohne die bestehenden JSON- und DB-Verträge zu ändern.

**Backend (Java Records im `domain`-Paket):**

- `ImdbId(String value)` — validiert `tt\w+` im Kompaktkonstruktor.
- `ReleaseYear(int value)` — besitzt die Regel `value == 0 → "Not yet released"` (`display()`).
- `WatchlistDate(String value)` — typisiert das Hinzufüge-Datum, ist `Comparable` und bietet
  `toLocalDate()`;
  hält den ISO-Rohstring, damit die String-Sortierung chronologisch bleibt.

Die Verträge bleiben stabil über drei Techniken:

- **Jackson** `@JsonValue` / `@JsonCreator` →
  serialisiert als blanker String bzw. blanke Zahl (`imdbId` bleibt `"tt…"`, `year` bleibt eine
  Zahl, `added` bleibt ein String).
- **JPA** `@Converter(autoApply = true)` je Value Object →
  die Spalten bleiben `varchar`/`integer`, die Entities tragen die Value Objects ohne
  Feld-Annotationen.
- **Spring MVC** `Converter<String, ImdbId>` →
  `@RequestParam ImdbId` bindet direkt;
  ein ungültiger Wert wird zu **400** (die Validierung liegt in `ImdbId`).

**Frontend (TypeScript, `core/domain.ts`):** **Branded Types** (`ImdbId = string & {__brand}`,
`ReleaseYear = number & {__brand}`, `WatchlistDate = string & {__brand}`) plus Smart-Constructors.
Zur Laufzeit sind es weiterhin die vom JSON gelieferten String/Number-Werte (kein Mapping, kein
Bruch von `http.get<T>()`);
zur Compile-Zeit verhindert das Brand die Vertauschung.
Die kleine Domänenlogik (`imdbUrl`, `releaseYearDisplay`) wohnt ebenfalls hier.

**Bewusst NICHT umgestellt** (primitiv belassen): `username`, `name`/`streamingServiceName`,
`languages` —
reine Strings ohne eigene Regeln oder Verwechslungsrisiko.
`price` ist bereits ein Value Object (`Price`).
Das Hinzufüge-Datum wurde als **string-basiertes** `WatchlistDate` (nicht striktes `LocalDate`)
modelliert, weil das Quellformat extern ist;
striktes Parsen beim Import würde sonst Zeilen mit abweichendem Format still verwerfen.

## Consequences

**Einfacher / besser:**

- Validierung und Domänenregeln je Konzept an **einer** Stelle;
  eine `ImdbId` in der Hand ist immer wohlgeformt.
- Typsicherheit über den ganzen Stack;
  ein `String` kann nicht mehr versehentlich als IMDb-Id hindurchrutschen (Backend hart, Frontend
  per Brand).
- Ein Zuhause für Domänenlogik (`imdbUrl`, `ReleaseYear.display`), inkl. Nebeneffekt:
  Katalog- und Flatrate-Tabelle zeigen jetzt „Not yet released" statt `0`.
- JSON- und DB-Verträge unverändert (durch Jackson- + JPA-Converter abgesichert;
  die `@WebMvcTest`-Slices prüfen `imdbId:"tt…"`, `year:`Zahl, `added:`String).

**Schwieriger / Nachteile:**

- Mehr Boilerplate:
  je Value Object ein JPA-Converter (+ ein Spring-Converter für die IMDb-Id) und die
  Jackson-Annotationen.
- **JPQL-Fallstrick:** `select distinct w.imdbId` auf ein Value-Object-Feld lässt Spring Data einen
  DTO-Konstruktor-Ausdruck (`new ImdbId(...)`) erzeugen und scheitern;
  gelöst über eine native Query auf die Rohspalte + Wrapping in einer `default`-Methode.
- **Objektgleichheit:** `!=` auf dem früheren `int`-Jahr wurde zu einem Identitätsvergleich;
  in `WatchlistImportService.differs` auf `Objects.equals` korrigiert (sonst meldet jeder Re-Import
  eine Änderung).
- Test-Churn:
  Literale (`"tt1"`, `2020`, `"2020-01-01"`) müssen in Value Objects bzw. Smart-Constructors
  verpackt werden;
  im Frontend etwas Reibung durch die Branded Types in den Fixtures.

## Alternatives Considered

- **Primitive beibehalten:** verworfen —
  genau die verstreute Validierung und Vertauschbarkeit sind der Auslöser.
- **Frontend: echte Klassen mit Laufzeit-Mapping** statt Branded Types: verworfen —
  bräuchte in jedem API-Service ein Mapping vom geparsten JSON auf Instanzen und bräche das direkte
  `http.get<T>()`;
  mehr Code und Laufzeitkosten für denselben Compile-Zeit-Nutzen.
- **`added` als striktes `LocalDate`:** verworfen —
  das externe Quellformat ist nicht garantiert ISO;
  striktes Parsen würde beim Import Zeilen verlieren.
  `WatchlistDate.toLocalDate()` bietet die Datumssemantik dort, wo sie gebraucht wird, ohne dieses
  Risiko.
