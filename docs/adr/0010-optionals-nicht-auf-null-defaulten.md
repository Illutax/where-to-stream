# 0010. Repository-Optionals nicht auf `null` defaulten

- **Date**: 2026-07-27
- **Status**: Accepted

## Context

Spring-Data-Repositories geben Nachschlage-Ergebnisse als `Optional` zurück, genau damit „nicht
gefunden" nicht wieder als `null` durch den Code wandert. Im `PosterService` war das aber
unterlaufen:

```java
TitlePoster row = repository.findByImdbId(imdbId).orElse(null);
if (row == null || (row.getPosterPath() == null && !isNegativeFresh(row, now))) {
    row = (row == null) ? repository.save(TitlePoster.of(...)) : refresh(...);
}
```

Das hat zwei Probleme:

- **`null` zurück in der Kontrollfluss-Logik.** Das `Optional` wird sofort per `.orElse(null)`
  eingeebnet und danach mit `if (row == null)` verzweigt — also exakt das Muster, das `Optional`
  vermeiden soll. Der Rest der Methode muss den `null`-Fall mittragen.
- **Es hat einen Nebenläufigkeitsfehler kaschiert.** `title_poster.imdb_id` ist `unique`. Das
  „`null` → also neu anlegen"-`save` ist nicht rennsicher: Beim ersten Aufruf eines Titels feuern
  Thumbnail (Zeile) und Hover (Vollbild) parallel, beide lesen „kein Row", beide `INSERT`en — der
  zweite läuft beim Commit in `Duplicate entry '…' for key 'imdb_id'` (500). Die
  `orElse(null)`-Verzweigung machte das Find-or-Create-Muster unsichtbar.

Die restliche Codebasis macht es bereits richtig — `StreamInfoService.resolve` verkettet
`result.filter(…).map(…).orElseGet(() -> fetch(…))` ohne je `null` anzufassen.

## Decision

**`Optional` aus Repositories (und vergleichbaren Lookups) wird funktional konsumiert** —
`map` / `flatMap` / `filter` / `ifPresent` / `orElseGet` — und **nicht** per `.orElse(null)` in
`null` eingeebnet, um danach darauf zu verzweigen. Vorbild ist `StreamInfoService`.

Konkret wurde `PosterService.resolve` umgeschrieben:

```java
final TitlePoster row = repository.findByImdbId(imdbId)
        .map(existing -> reDiscoverStaleNegative(existing, now))
        .orElseGet(() -> discover(imdbId, now));
if (row.getPosterPath() == null) {
    return Optional.empty();                 // Negativ-Cache
}
return cachedBytes(row, size).or(() -> downloadAndStore(row, size));
```

Das legt zugleich das Find-or-Create offen und macht die Rennsicherheit **explizit**: Die
öffentlichen Einstiegspunkte rufen das transaktionale `resolve` über den eigenen Proxy und
wiederholen einmal bei `DataIntegrityViolationException` — der Retry läuft in einer frischen
Transaktion und liest schlicht die Zeile, die der andere Request committet hat.

**Abgrenzung (bewusst erlaubt):** Ein *absichtlich nullbarer Datenwert* ist kein Verstoß. Dazu
zählen ein nullbares DTO-Feld (`WatchlistDto.lastImportedAt == null` = „nie importiert",
`OverviewEntryDto.services == null` = „N/A") und der persistierte Negativ-Cache-Marker
(`title_poster.poster_path == null` = „kein Poster"). Verboten ist `null` als **Sentinel für
„nicht gefunden"** mit anschließender `null`-Verzweigung, nicht ein Feld, dessen Domänenwert
legitim „keiner" ist.

## Consequences

**Einfacher / besser:**

- Kein `null` mehr im Kontrollfluss des `PosterService`; die Methode liest sich als Pipeline.
- Der Duplicate-Key-Bug ist behoben und die Rennbehandlung ist benannt statt zufällig.
- Einheitlich mit `StreamInfoService`.

**Schwieriger / Nachteile:**

- **Nicht maschinell erzwungen.** ArchUnit sieht Typen/Methoden, nicht den Ausdruck `.orElse(null)`
  an einer Repository-Rückgabe; die Regel ist eine Review-Konvention (wie die Nygard-ADRs
  festhalten *warum*, nicht *wie*).
- Der Retry braucht einen Selbst-Proxy (`ObjectProvider<PosterService>`), weil ein direkter
  Selbstaufruf den transaktionalen Proxy umginge.

## Alternatives Considered

- **`orElse(null)` beibehalten:** verworfen — bringt `null` in den Kontrollfluss zurück und hat
  hier den Nebenläufigkeitsfehler verdeckt.
- **DTO-Felder als `Optional` modellieren:** verworfen — `Optional` als Feld/JSON-Wert ist ein
  Anti-Pattern und würde den bestehenden JSON-Vertrag (`lastImportedAt: null`) brechen. Ein
  nullbarer Wert bleibt der richtige Ausdruck für „legitim keiner".
