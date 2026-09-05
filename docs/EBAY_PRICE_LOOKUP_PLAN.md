# Plan: eBay-Preisabfrage pro Titel im Dashboard

Ziel dieses Dokuments ist ein Umsetzungsplan für die eBay-Anbindung von w2s.
Es ist bewusst so geschrieben, dass eine **andere Claude-Code-Session ohne Vorwissen aus dem
Ursprungsgespräch** direkt damit arbeiten kann: jeder Schritt nennt die konkrete Datei, die
betroffenen Klassen/Komponenten und was sich ändert.

**Status: Entwurf, in Arbeit.**
Der Plan wird schrittweise vervollständigt.
Abschnitte, die noch auf eine Analyse warten, sind mit **OFFEN** markiert.

## 1. Ziel aus Nutzersicht

Neben jedem Titel auf dem Dashboard (Startseite) steht ein Knopf.
Wird er gedrückt, sucht die Anwendung im Hintergrund auf eBay nach diesem Filmtitel und ermittelt
zwei Preise:

1. den **günstigsten Sofortkauf** (Buy It Now / Festpreis),
2. den **günstigsten laufenden Auktionspreis** (aktuelles Höchstgebot einer noch laufenden Auktion).

Beide Preise werden anschließend beim Titel im Dashboard angezeigt.

Ausdrückliche Vorgabe des Auftraggebers:

- Die Abfrage soll **aus dem Client heraus** laufen (Browser, nicht Server).
- **Security** ist ein Schwerpunkt der Umsetzung, kein Nachgedanke.

## 2. Ausgangslage (Ist-Zustand, Stand 2026-09-05)

- Das Dashboard ist `OverviewPage`
  (`src/main/frontend/src/app/features/overview/overview-page.ts`).
  Es lädt einmalig `GET /api/catalog` (`CatalogApi`) und rendert je nach
  `UserPrefsStore.viewMode()` entweder `CatalogTable` (Tabelle) oder `TitleGrid` (Poster-Grid).
  Es ist die einzige „smarte" Komponente des Dashboards — alles darunter ist präsentational.
- Die Zeilen-/Kachel-Daten sind `OverviewEntry` (`core/models.ts`):
  `isRated`, `name`, `imdbId`, `year`, `added`, `services`.
  **Kein Preisfeld vorhanden**; die Struktur spiegelt 1:1 das Server-DTO
  (`CatalogPageDto`/`OverviewEntryDto`), Client-seitiges Umformen findet bewusst nicht statt.
- Die Titeldarstellung liegt in zwei präsentationalen Komponenten:
  `TitleCell` (`shared/title-cell/title-cell.ts`) für die Tabelle und
  `TitleTile` (`shared/title-tile/title-tile.ts`) für das Grid.
  Beide werden auch von den Provider-Seiten genutzt — eine Änderung dort wirkt nicht nur auf dem
  Dashboard.
- Externe Links werden heute rein clientseitig gerendert:
  `imdbUrl()` in `core/domain.ts` plus `<a target="_blank" rel="noopener">` in `TitleCell`/`TitleTile`.
  Das ist der einzige bestehende Fall von „Client spricht direkt mit einer fremden Domain" —
  und er ist ein reiner Link, kein HTTP-Aufruf.
- Der einzige bestehende Fall einer **Freitextsuche gegen einen Fremdanbieter** läuft dagegen
  **serverseitig**: `ImdbSuggestionSource`
  (`src/main/java/tech/dobler/where2stream/titlecatalog/adapter/out/imdb/ImdbSuggestionSource.java`)
  mit eigenem `RateLimiter`, Timeout, Fehler-Degradation auf leere Liste und
  `@ConfigurationProperties`-Bindung (`ImdbSearchProperties`);
  davor `ImdbSearchService` (reichert um „ist auf meiner Watchlist an") und
  `ImdbSearchApiController` (`GET /api/imdb/search`).
  Der Client (`ImdbSearchApi`, `ImdbSearchBox`) debounced 1 s und ruft nur die eigene API.
  **Das ist der Architektur-Präzedenzfall für alles, was eine fremde API abfragt.**
- Der bestehende werstreamt.es-Cache ist **global** (nach `imdbId`, benutzerübergreifend geteilt),
  mit TTL und gestaffeltem Hintergrund-Refresh (ADR-0016) — das Muster, an dem sich ein
  Preis-Cache orientieren müsste.
- `SecurityConfig`
  (`src/main/java/tech/dobler/where2stream/accountaccess/adapter/in/security/SecurityConfig.java`)
  konfiguriert **keine** `.headers(...)` — es gibt aktuell **keine Content-Security-Policy**.
  Es gelten nur die Spring-Security-Defaults (u.a. `X-Content-Type-Options`, `X-Frame-Options`),
  eine CSP ist darin nicht enthalten.
  Für dieses Feature ist das doppelt relevant:
  ohne CSP fällt ein direkter Browser-Call zu eBay nicht auf — und mit CSP müsste man sie gezielt
  für eBay öffnen.
- Es gibt **keinerlei** eBay-Bezug im Projekt: `grep -rin ebay` über das Repository liefert
  null Treffer. Alles hier ist Neuland.

## 3. Die zentrale technische Frage: geht „vom Client aus" überhaupt?

Vorab-Recherche (Stand dieses Entwurfs, teilweise noch zu verifizieren):

- Die **Finding API** (`findItemsByKeywords`) war die einzige eBay-API, die sich per JSONP mit
  bloßer AppID direkt aus dem Browser aufrufen ließ.
  Sie wurde zum **05.02.2025 abgeschaltet** (Deprecation seit 04.01.2024), ebenso die Shopping API.
  Dieser Weg existiert nicht mehr.
- Die **Browse API** (`buy/browse/v1/item_summary/search`) ist der offizielle Nachfolger.
  Sie verlangt einen OAuth-Application-Token, der aus `client_id`/`client_secret` erzeugt wird,
  und ist server-to-server ausgelegt.
- Damit steht die Anforderung „vom Client aus" im direkten Konflikt mit der Anforderung
  „Security": ein Browser-Call bräuchte Zugangsdaten im ausgelieferten JS-Bundle.

**OFFEN — wird aus der laufenden Sicherheits-/Machbarkeitsanalyse ergänzt:**

- Sendet die Browse API CORS-Header, ist ein Browser-Aufruf also technisch überhaupt möglich?
- Liefert die Browse API laufende **Auktionen mit aktuellem Gebotspreis**, oder nur Festpreise?
  (Die Zweiteilung Sofortkauf/Auktion ist die Kernanforderung — wenn die API den Auktionsteil
  nicht liefert, fällt das halbe Feature.)
- Gibt es andere clientseitig nutzbare Endpunkte (RSS, öffentliche JSON-Endpunkte, EPN)?
- Was ist die minimale Backend-Beteiligung, die die Idee rettet?

## 4. Umsetzungsvarianten

| # | Variante | Preise im Dashboard? | Client spricht mit eBay? | Backend nötig |
| --- | --- | --- | --- | --- |
| A | Deep-Link „auf eBay suchen" (neuer Tab) | nein | nein (nur Link) | nein |
| B | Direkter Browse-API-Call aus dem Browser | ja | ja | Token-Beschaffung ungeklärt |
| C | Backend-Adapter + globaler Preis-Cache, Client ruft `/api/…` | ja | nein | ja |
| D | Hybrid: Deep-Link sofort, Preise als zweiter Ausbauschritt über C | schrittweise | nein | ja (Stufe 2) |

Variante A war der ursprüngliche Entwurf dieses Plans;
sie erfüllt die jetzt präzisierte Anforderung (Preise **im** Dashboard) **nicht** und bleibt hier
nur als Fallback bzw. als Stufe 1 von Variante D stehen.

**OFFEN:** Bewertung und Empfehlung nach der Sicherheitsanalyse.

## 5. Sicherheitsanforderungen

**OFFEN — dieser Abschnitt ist der Kern des Plans und wird aus der Analyse gefüllt.**
Zu behandeln sind mindestens:

- Secret-Exposure (Token/`client_secret` im Bundle, Missbrauchspotenzial, Account-Sperrung).
- XSS/Injection beim Rendern fremder, benutzergenerierter eBay-Inhalte
  (Angebotstitel, Bild-URLs, Verkäufernamen) in Angular-Templates —
  insbesondere `[href]`/`[src]`-Bindings.
- Content-Security-Policy: heute nicht vorhanden; was müsste geöffnet werden, was kostet das.
- Abuse/DoS: ein Knopf pro Titel × N Titel, clientseitiges Rate-Limiting ist nicht durchsetzbar.
- Datenschutz: Client-Call gibt die Benutzer-IP an eBay; Server-Call bündelt alles auf der
  Server-IP (Blocking-Risiko).
- Cache-Datenhaltung: global (wie werstreamt.es) oder pro Benutzer, und ob Preisdaten
  personenbezogen werden, wenn sie an einer Watchlist hängen.
- eBay-Nutzungsbedingungen zu API-Nutzung, Scraping, Anzeige und Caching-Dauer von Preisdaten.

## 6. Offene Entscheidungen

1. **Variante** (siehe Abschnitt 4) — hängt am Ergebnis der Analyse.
2. **Suchbegriff:** deutscher Titel (vorhanden über `injectTitleMeta`, nur wenn die
   Präferenz „Deutsche Titel" an ist) oder Originalname; mit oder ohne Jahr;
   Kategoriefilter auf „DVDs & Blu-ray Discs".
   Ein Jahr im Suchbegriff senkt bei eBay den Recall spürbar (AND-Verknüpfung der Keywords,
   Editionsjahre weichen vom Erscheinungsjahr ab).
3. **Marktplatz:** fest `ebay.de` oder abgeleitet aus `UserPrefsStore.language()`.
4. **Scope:** nur Dashboard oder überall, wo `TitleCell`/`TitleTile` gerendert wird
   (Provider-Seiten nutzen dieselben Komponenten).
5. **Persistenz der Preise:** flüchtig im Client (nur für die Sitzung) oder serverseitig gecacht
   mit TTL analog `wer-streamt.invalidate.after-days`.
6. **Anzeigeort:** eigene Tabellenspalte, Chip auf der Poster-Kachel, oder Aufklapp-Detail.
   Beides muss in Tabelle **und** Grid funktionieren.
7. **EPN-Affiliate-Parameter** an ausgehenden Links: Geschäftsentscheidung, nicht Technik.

## 7. Phasenplan

**OFFEN — wird nach der Variantenentscheidung ausgearbeitet.**

## 8. Nicht-Ziele

- Kein Kaufabschluss, keine Gebotsabgabe, keine eBay-Anmeldung des Benutzers aus w2s heraus.
- Keine Preishistorie/Preisalarme in dieser Ausbaustufe.
- Keine Verkäufer-, Versand- oder Zustandsbewertung — nur die zwei geforderten Preise.

## 9. Änderungshistorie

- **2026-09-05** — Entwurf v1: Zielbild, Ist-Zustand, Varianten, offene Punkte.
  Sicherheitsanalyse läuft noch.
