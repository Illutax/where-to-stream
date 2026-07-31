# 0016. Asynchrone, verzögerte Aktualisierung des Verfügbarkeits-Caches statt synchronem Reload beim Seitenaufruf

- **Date**: 2026-07-30
- **Status**: Accepted

## Context

[ADR-0012](0012-permanenter-titel-cache-vs-ttl-verfuegbarkeits-cache.md) legt fest, dass der
Streaming-Verfügbarkeits-Cache (`query_meta`, `query_result`) TTL-basiert ist
(`wer-streamt.invalidate.after-days`, Default 28 Tage) und zusätzlich von einem ADMIN gezielt
vorzeitig invalidiert werden kann (`POST /api/manage/invalidate`, `/manage`-UI, „Cache Verwalten").

Was das ADR nicht festhält: **wie** ein abgelaufener oder invalidierter Eintrag tatsächlich neu
geladen wird. Das passiert heute ausschließlich synchron, innerhalb des HTTP-Requests, der zufällig
zuerst danach fragt:

- `StreamInfoService.resolveAll(imdbIds)` (aufgerufen von `CatalogOverviewService.overview()` fürs
  Dashboard und von `AggregateService.getAll()` für die Provider-Seiten) lädt die aktuell gültigen
  `QueryMeta`-Zeilen; für jeden Treffer, der fehlt, invalidiert oder über die TTL hinaus ist, wird
  **im selben Request** `StreamInfoService.resolve(imdbId)` aufgerufen, welches synchron
  `WerStreamtEsSource.query(imdbId)` scraped (durch den geteilten `RateLimiter`, Default
  2 req/s) und das Ergebnis persistiert, bevor der Request antwortet.
- Da der Cache **global** ist (nur nach `imdbId`, nicht pro Nutzer), reicht ein einziger
  Dashboard-Aufruf durch irgendeinen Nutzer, um jeden invalidierten/abgelaufenen Titel auf dessen
  Watchlist sofort wieder frisch zu cachen — unabhängig davon, ob und wann ein ADMIN über
  „Cache Verwalten" gezielt scrapen wollte.

Das hat zwei Konsequenzen, die dieses ADR adressiert:

1. **Die „Cache Verwalten"-Seite hat keinen beobachtbaren Effekt.** Ein ADMIN invalidiert Titel,
   um sie gezielt neu zu scrapen — aber sobald irgendjemand (oft der ADMIN selbst beim Testen) das
   Dashboard öffnet, ist der invalidierte Zustand bereits durch den impliziten Reload aufgelöst,
   bevor der „Scrapen"-Button der Manage-Seite etwas zu tun hätte. Die Seite wirkt wirkungslos,
   obwohl sie technisch funktioniert — sie wird nur ständig von der automatischen Dashboard-Logik
   überholt.
2. **Ein Seitenaufruf kann beliebig lange blockieren.** Sind viele Titel gleichzeitig abgelaufen
   oder invalidiert (z. B. direkt nach einem großen Watchlist-Import oder einer Massen-Invalidierung
   über die Manage-Seite), muss der nächste Dashboard-Request potenziell dutzende Titel seriell
   gegen den ratenlimitierten externen Scraper nachladen (`parallelStream` parallelisiert nur über
   Worker-Threads, der Request selbst wartet trotzdem auf das langsamste Ergebnis), bevor er
   überhaupt antwortet.

## Decision

Wir trennen **„zwischengespeicherte Daten anzeigen"** von **„veraltete Daten auffrischen"**:

1. `StreamInfoService.resolveAll(...)` liefert für einen abgelaufenen/invalidierten, aber
   **existierenden** Cache-Eintrag sofort die zwischengespeicherten (u. U. veralteten) Werte zurück,
   statt zu blockieren, und markiert das Ergebnis als `stale`. Nur ein Titel, der **noch nie**
   gecacht wurde, wird weiterhin synchron aufgelöst (es gibt sonst nichts anzuzeigen).
2. Für jeden als `stale` erkannten Titel wird — dedupliziert gegen parallele Anfragen für denselben
   Titel — im Hintergrund (`@Async`) ein Refresh angestoßen, der denselben, bereits vorhandenen
   `resolve(imdbId, forceRefresh = true)`-Pfad nutzt.
3. Das Dashboard und die Provider-Seiten zeigen einen kleinen Hinweis-Banner, wenn die angezeigten
   Daten (teilweise) veraltet sind — eine Aggregat-Information pro Seite, keine Kennzeichnung pro
   Zeile (YAGNI: nicht mehr Sichtbarkeit bauen als angefragt).
4. Zusätzlich zum bedarfsgetriebenen (Seitenaufruf-getriggerten) Refresh übernimmt ein
   **Scheduled Job** proaktiv das Auffrischen von Titeln, die niemand zeitnah ansieht: Er läuft in
   grobem Takt (initial: täglich) und aktualisiert nur Titel, deren TTL **plus einem zufälligen
   Jitter-Faktor zwischen dem 1,5- und 2-fachen von `wer-streamt.invalidate.after-days`** bereits
   verstrichen ist, sowie alle manuell invalidierten Titel. Der Jitter wird **einmalig beim
   Schreiben** eines Cache-Eintrags gewürfelt und persistiert (`due_for_refresh_at`), nicht bei
   jedem Job-Lauf neu berechnet — das verteilt die Refresh-Zeitpunkte vieler gleichzeitig
   importierter/gecachter Titel, statt sie synchron gemeinsam ablaufen zu lassen
   (Thundering-Herd-Vermeidung), und bleibt stabil nachvollziehbar (derselbe Eintrag hat immer
   denselben Fälligkeits-Zeitpunkt, unabhängig davon, wie oft der Job seitdem gelaufen ist).

Der volle Implementierungsplan (Phasen, betroffene Klassen, Config, Migration, Tests) steht in
[`docs/CACHE_REFRESH_PLAN.md`](../CACHE_REFRESH_PLAN.md).

## Consequences

**Einfacher / vorteilhaft:**

- Die „Cache Verwalten"-Seite bekommt ihre Funktion zurück: Invalidieren + gezieltes Scrapen bleibt
  der einzige Weg, ein Neu-Scrapen **sofort und garantiert** auszulösen; ein Seitenaufruf des
  Dashboards nimmt ihr das nicht mehr vorweg.
- Seitenaufrufe bleiben schnell und vorhersehbar — kein Request blockiert mehr auf einer
  unbekannten Anzahl externer Scrapes.
- Titel, die niemand aktiv ansieht, veralten nicht unbegrenzt (der Scheduled Job holt sie
  irgendwann nach), ohne dass dafür ständige, unnötige Last entsteht (grober Takt, Jitter, keine
  Aktion bei „nichts fällig").

**Nachteile / bewusst in Kauf genommen:**

- Nutzer sehen für kurze Zeit (bis der Hintergrund-Refresh durch ist) explizit veraltete Daten
  statt garantiert frischer Daten — dafür der neue Banner, damit das sichtbar und nicht
  stillschweigend falsch ist.
- Erstmals `@Async`/`@EnableAsync` und `@Scheduled`/`@EnableScheduling` im Projekt (bislang nicht
  verwendet) — neue Infrastruktur, die getestet und betrieblich beobachtet werden will (Executor
  dimensionieren, Job-Ausführung loggen).
- Neue Spalte `due_for_refresh_at` auf `query_meta` (Liquibase-Migration).
- Etwas mehr Komplexität in `StreamInfoService` (stale-vs-fresh-Unterscheidung, In-Flight-Tracking
  gegen doppelte parallele Refreshes für denselben Titel).

## Alternatives Considered

- **Nichts ändern, Manage-Seite nur informativer machen** (Zeitstempel statt Boolean, aber
  Dashboard-Verhalten unangetastet lassen): behebt nur das kosmetische Symptom, nicht die
  eigentliche Redundanz — die Seite bliebe weiterhin ständig vom Dashboard überholt. Trotzdem als
  Teil dieses Plans übernommen (Phase 1), weil die Information für sich genommen nützlich ist,
  aber nicht als alleinige Lösung ausreichend.
- **Refresh nur über einen Scheduled Job, kein bedarfsgetriebener Async-Pfad beim Seitenaufruf**:
  einfacher, aber ein Titel, der frisch invalidiert wurde und sofort angesehen wird, bliebe bis zum
  nächsten Job-Lauf veraltet, obwohl ein Nutzer gerade aktiv danach schaut — schlechtere UX für den
  Normalfall.
- **Jitter bei jedem Job-Lauf neu würfeln statt beim Schreiben zu persistieren**: spart die neue
  Spalte, macht die Fälligkeit eines Eintrags aber von der Zufallslogik des jeweiligen Laufs
  abhängig statt von einer stabilen, am Eintrag selbst ablesbaren Eigenschaft — schwerer zu testen
  und nachzuvollziehen. Verworfen zugunsten des persistierten Werts.
