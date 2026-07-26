# 0010. Optionals nicht auf `null` defaulten (funktional konsumieren)

- **Date**: 2026-07-27
- **Status**: Accepted

## Context

`Optional` gibt es, damit „kein Wert" **nicht** wieder als `null` (oder als geworfene Exception)
durch den Code wandert. Ein `Optional` entsteht an vielen Stellen — Repository-Lookups
(`findByImdbId`), Quell-Abfragen (`PosterSource.findPosterPath` / `download`), Domänen-Helfer
(`toAvailableServiceNames`), Stream-Operationen (`findFirst`) —, und die Regel soll für **alle**
gelten, unabhängig von der Herkunft.

Ausgelöst hat das ADR ein konkreter Verstoß im `PosterService`:

```java
TitlePoster row = repository.findByImdbId(imdbId).orElse(null);
if (row == null || (row.getPosterPath() == null && !isNegativeFresh(row, now))) {
    row = (row == null) ? repository.save(TitlePoster.of(...)) : refresh(...);
}
```

Zwei Ausprägungen desselben Grundproblems tauchen in Codebasen typisch auf:

- **`.orElse(null)` + `null`-Verzweigung.** Das `Optional` wird sofort eingeebnet und danach mit
  `if (x == null)` verzweigt — also genau das, was `Optional` vermeiden soll. Im `PosterService`
  hat dieses Muster zusätzlich einen Nebenläufigkeitsfehler verdeckt: Das „`null` → also neu
  anlegen"-`save` war nicht rennsicher, `title_poster.imdb_id` ist `unique`, und zwei parallele
  Erstzugriffe (Thumbnail + Hover) liefen beim Commit in `Duplicate entry '…' for key 'imdb_id'`.
- **Unsichere Entnahme.** `optional.get()` ohne Prüfung bzw. `isPresent()` + `get()` wirft bei
  Abwesenheit statt den Fall zu behandeln — die Kehrseite derselben Medaille.

Die Codebasis macht es überwiegend schon richtig — `StreamInfoService.resolve` verkettet
`result.filter(…).map(…).orElseGet(() -> fetch(…))`, ohne je `null` oder `get()` anzufassen.

## Decision

**Jedes `Optional` — egal welcher Herkunft — wird funktional konsumiert** (`map` / `flatMap` /
`filter` / `or` / `orElse(wert)` / `orElseGet` / `ifPresent` / `orElseThrow`) und **nicht**

- per `.orElse(null)` in `null` eingeebnet, um danach darauf zu verzweigen, noch
- per ungeprüftem `.get()` / `isPresent()`+`get()` entnommen (stattdessen `orElseThrow(…)`, wenn
  Anwesenheit invariant ist — das macht die Invariante explizit und liefert bei Bruch eine klare
  Fehlermeldung).

Umgesetzt:

- `PosterService.resolve` funktional umgeschrieben (Vorbild `StreamInfoService`):

  ```java
  final TitlePoster row = repository.findByImdbId(imdbId)
          .map(existing -> reDiscoverStaleNegative(existing, now))
          .orElseGet(() -> discover(imdbId, now));
  if (row.getPosterPath() == null) return Optional.empty();     // Negativ-Cache
  return cachedBytes(row, size).or(() -> downloadAndStore(row, size));
  ```

  Das legt zugleich das Find-or-Create offen und macht die Rennsicherheit explizit (Retry über den
  eigenen Proxy bei `DataIntegrityViolationException`).
- `AggregateService.includedFrom`: `watchlistCatalog.findByImdb(...).get()` →
  `.orElseThrow(...)` mit Invarianten-Meldung.

**Abgrenzung (bewusst erlaubt):** Ein *absichtlich nullbarer Datenwert* ist kein Verstoß — ein
nullbares DTO-Feld (`WatchlistDto.lastImportedAt == null` = „nie importiert",
`OverviewEntryDto.services == null` = „N/A", `QueryResult.languages == null`) oder der persistierte
Negativ-Cache-Marker (`title_poster.poster_path == null` = „kein Poster"). Verboten ist `null` als
**Sentinel für „nicht gefunden"** mit anschließender Verzweigung, nicht ein Feld, dessen Domänenwert
legitim „keiner" ist. `Optional` selbst wird **nicht** als Feld- oder DTO-/JSON-Typ verwendet.

## Consequences

**Einfacher / besser:**

- Kein `null` im Kontrollfluss und keine ungeprüfte Entnahme mehr; die Methoden lesen sich als
  Pipeline. Einheitlich mit `StreamInfoService`.
- Der Duplicate-Key-Bug ist behoben und die Rennbehandlung ist benannt statt zufällig.

**Schwieriger / Nachteile:**

- **Nicht maschinell erzwungen.** ArchUnit sieht Typen/Methoden, nicht den Ausdruck `.orElse(null)`
  bzw. `.get()` an einer `Optional`-Rückgabe; die Regel ist eine Review-Konvention (die ADRs halten
  *warum* fest, nicht *wie*).
- Der Retry im `PosterService` braucht einen Selbst-Proxy (`ObjectProvider<PosterService>`), weil
  ein direkter Selbstaufruf den transaktionalen Proxy umginge.

## Alternatives Considered

- **Regel auf Repository-Optionals beschränken:** verworfen — das Grundproblem hängt nicht an der
  Herkunft; ein `Optional` aus einer Quelle oder einem Stream verdient dieselbe Behandlung.
- **`.orElse(null)` / `.get()` beibehalten:** verworfen — bringt `null` in den Kontrollfluss zurück
  bzw. wirft unkontrolliert; ersteres hatte hier sogar den Nebenläufigkeitsfehler verdeckt.
- **DTO-Felder als `Optional` modellieren:** verworfen — `Optional` als Feld/JSON-Wert ist ein
  Anti-Pattern und bräche den bestehenden JSON-Vertrag (`lastImportedAt: null`). Ein nullbarer Wert
  bleibt der richtige Ausdruck für „legitim keiner".
