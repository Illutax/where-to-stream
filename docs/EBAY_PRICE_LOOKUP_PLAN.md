# Plan: eBay-Preisabfrage pro Titel im Dashboard

Ziel dieses Dokuments ist ein Umsetzungsplan für die eBay-Anbindung von w2s.
Es ist bewusst so geschrieben, dass eine **andere Claude-Code-Session ohne Vorwissen aus dem
Ursprungsgespräch** direkt damit arbeiten kann: jeder Schritt nennt die konkrete Datei, die
betroffenen Klassen/Komponenten und was sich ändert.

**Status: Entwurf v2, in Arbeit.**
Machbarkeit und Sicherheitslage sind geklärt (Abschnitte 3–5);
offen ist die Variantenentscheidung des Auftraggebers (Abschnitt 4) und damit die
Detailausarbeitung ab Phase 2.

## 1. Ziel aus Nutzersicht

Neben jedem Titel auf dem Dashboard (Startseite) steht ein Knopf.
Wird er gedrückt, sucht die Anwendung im Hintergrund auf eBay nach diesem Filmtitel und ermittelt
zwei Preise:

1. den **günstigsten Sofortkauf** (Buy It Now / Festpreis),
2. den **günstigsten laufenden Auktionspreis** (aktuelles Höchstgebot einer noch laufenden Auktion).

Beide Preise werden anschließend beim Titel im Dashboard angezeigt.

Ausdrückliche Vorgaben des Auftraggebers:

- Die Abfrage soll **aus dem Client heraus** laufen (Browser, nicht Server).
- **Security** ist ein Schwerpunkt der Umsetzung, kein Nachgedanke.

Diese beiden Vorgaben stehen im Konflikt zueinander — siehe Abschnitt 3.
Die Empfehlung dieses Plans erhält das **Nutzererlebnis** (Knopf, Hintergrundabfrage, Preise im
Dashboard) vollständig und verlegt nur die Netzwerkstrecke.

## 2. Ausgangslage (Ist-Zustand, Stand 2026-09-05)

- Das Dashboard ist `OverviewPage`
  (`src/main/frontend/src/app/features/overview/overview-page.ts`).
  Es lädt einmalig `GET /api/catalog` (`CatalogApi`) und rendert je nach
  `UserPrefsStore.viewMode()` entweder `CatalogTable` (Tabelle) oder `TitleGrid` (Poster-Grid).
  Es ist die einzige „smarte" Komponente des Dashboards — alles darunter ist präsentational.
- Die Zeilen-/Kachel-Daten sind `OverviewEntry` (`core/models.ts`):
  `isRated`, `name`, `imdbId`, `year`, `added`, `services`.
  **Kein Preisfeld vorhanden**; die Struktur spiegelt 1:1 das Server-DTO
  (`CatalogPageDto`/`OverviewEntryDto`), clientseitiges Umformen findet bewusst nicht statt.
- Die Titeldarstellung liegt in zwei präsentationalen Komponenten:
  `TitleCell` (`shared/title-cell/title-cell.ts`) für die Tabelle und
  `TitleTile` (`shared/title-tile/title-tile.ts`) für das Grid.
  Beide werden auch von den Provider-Seiten genutzt — eine Änderung dort wirkt nicht nur auf dem
  Dashboard.
- Externe Links werden heute rein clientseitig gerendert:
  `imdbUrl()` in `core/domain.ts` plus `<a target="_blank" rel="noopener">` in `TitleCell`/`TitleTile`.
  Das ist der einzige bestehende Fall von „Client spricht direkt mit einer fremden Domain" —
  und er ist ein reiner Link, kein HTTP-Aufruf.
- Der einzige bestehende Fall einer **Abfrage gegen einen Fremdanbieter** läuft dagegen
  **serverseitig**: `ImdbSuggestionSource`
  (`src/main/java/tech/dobler/where2stream/titlecatalog/adapter/out/imdb/ImdbSuggestionSource.java`)
  mit eigenem `RateLimiter`, Timeout, Fehler-Degradation auf leere Liste und
  `@ConfigurationProperties`-Bindung (`ImdbSearchProperties`);
  davor `ImdbSearchService` und `ImdbSearchApiController` (`GET /api/imdb/search`).
  Der Client (`ImdbSearchApi`, `ImdbSearchBox`) debounced 1 s und ruft nur die eigene API.
  **Das ist der Architektur-Präzedenzfall für alles, was eine fremde API abfragt** —
  ebenso wie `ImdbTitleSource`/`TmdbPosterSource` (Poster/Metadaten) und `WerStreamtEsSource`.
- Der bestehende werstreamt.es-Cache ist **global** (nach `imdbId`, benutzerübergreifend geteilt),
  mit TTL und gestaffeltem Hintergrund-Refresh (ADR-0016) — das Muster, an dem sich ein
  Preis-Cache orientiert.
- Poster fremder Herkunft werden **nicht** vom Browser bei der Fremd-CDN geladen, sondern
  serverseitig geholt, in der DB gecacht und same-origin über `/api/titles/{id}/poster`
  ausgeliefert. Das ist das etablierte Muster für „fremde Inhalte, ohne den Browser des Nutzers
  zur Fremddomain zu schicken".
- `SecurityConfig`
  (`src/main/java/tech/dobler/where2stream/accountaccess/adapter/in/security/SecurityConfig.java`)
  konfiguriert **keine** `.headers(...)`, und `index.html` enthält kein CSP-Meta-Tag —
  es gibt aktuell **keine Content-Security-Policy**, nur die Spring-Security-Defaults.
- Ein **Per-User-Rate-Limit auf API-Endpunkten existiert nirgends im Projekt**.
  Der vorhandene `RateLimiter` (`shared/platform/outbound/RateLimiter.java`) drosselt
  *ausgehende* Requests global, nicht eingehende pro Benutzer.
- Es gibt **keinerlei** eBay-Bezug im Projekt: `grep -rin ebay` über das Repository liefert
  null Treffer. Alles hier ist Neuland.

## 3. Machbarkeit: „vom Client aus" ist nicht umsetzbar

Ergebnis der Recherche (Belege am Ende des Abschnitts):

- **Die Finding API** (`findItemsByKeywords`) war die einzige eBay-API, die sich per JSONP mit
  bloßer AppID direkt aus dem Browser aufrufen ließ.
  Sie wurde zum **05.02.2025 abgeschaltet** (deprecated seit 04.01.2024), ebenso die Shopping API.
  Dieser Weg existiert nicht mehr.
- **Die Browse API** (`buy/browse/v1/item_summary/search`) ist der Nachfolger und verlangt ein
  **Application Access Token** aus dem OAuth2-Client-Credentials-Grant:
  `POST https://api.ebay.com/identity/v1/oauth2/token` mit
  `Authorization: Basic base64(client_id:client_secret)`.
  Das `client_secret` ist zwingend, ein Browser-Flow ist nicht vorgesehen,
  und der OAuth-Endpunkt sendet **keine CORS-Header** — ein Browser-Aufruf scheitert bereits am
  Preflight.
- Ein reiner **Token-Broker** (Backend beschafft das Token, Browser ruft eBay damit direkt) rettet
  die Idee nicht:
  das ausgehändigte Token ist aus dem Browser extrahierbar, und die Datenendpunkte der Browse API
  sind ebenfalls nicht als CORS-offen dokumentiert.
- Die verbleibenden „clientfähigen" Wege — Anzapfen der Website-JSON-Endpunkte — sind **Scraping**
  und laut eBay-Nutzungsbedingungen untersagt (zusätzlich aggressive Anti-Bot-Maßnahmen).

**Die gute Nachricht:** inhaltlich liefert die Browse API genau das, was das Feature braucht.

- Der Filter `buyingOptions:{FIXED_PRICE}` bzw. `buyingOptions:{AUCTION}` trennt die beiden
  gewünschten Angebotsarten (Default ohne Filter ist nur Festpreis).
- Für Auktionen liefert `ItemSummary` das Feld **`currentBidPrice`** — das aktuelle Höchstgebot
  einer laufenden Auktion. Die Zweiteilung „günstigster Sofortkauf / günstigstes laufendes Gebot"
  ist also abbildbar.

**Die harte Randbedingung:** das Standardkontingent liegt bei **5.000 Calls pro Tag und
Applikation** — nicht pro Benutzer.
Bei zwei Calls je Titel (Sofortkauf + Auktion) sind das **2.500 Titelabfragen pro Tag für alle
Benutzer zusammen**.
Eine einzelne Watchlist mit 200 Titeln würde bei „alles laden" 400 Calls verbrauchen, also 16 %
des Tagesbudgets.
Ohne Cache und ohne Budgetbremse ist das Kontingent mit wenigen Nutzern erschöpft.
Mehr gibt es nur über eBays „Application Growth Check".

Quellen:
[Browse API Overview](https://developer.ebay.com/api-docs/buy/browse/overview.html),
[ItemSummary-Typ](https://developer.ebay.com/api-docs/buy/browse/types/gct:ItemSummary),
[Finding/Shopping-API-Abschaltung](https://community.ebay.com/t5/Traditional-APIs-Search/Alert-Finding-API-and-Shopping-API-to-be-decommissioned-in-2025/td-p/34222062),
[Q3-2024-Newsletter](https://developer.ebay.com/updates/newsletter/q3_2024),
[fehlendes CORS am OAuth-Endpunkt](https://github.com/eBay/ebay-oauth-nodejs-client/issues/13),
[API-Kontingent](https://community.ebay.com/t5/eBay-APIs-Talk-to-your-fellow/About-API-limit-calls/td-p/34671615),
[API License Agreement (Stand 24.06.2025)](https://developer.ebay.com/join/api-license-agreement).

## 4. Umsetzungsvarianten und Empfehlung

| # | Variante | Preise im Dashboard? | Client spricht mit eBay? | Machbar? | Security-Risiko | Aufwand |
| --- | --- | --- | --- | --- | --- | --- |
| A | Deep-Link „auf eBay suchen" (neuer Tab) | nein | nur als Link | ja, trivial | minimal | Stunden |
| B | Direkter Browse-API-Call aus dem Browser | ja | ja | **nein** | inakzeptabel | — |
| C | Backend-Adapter + globaler Preis-Cache, Client ruft `/api/…` | ja | nein | ja | gering | 1–3 Tage + eBay-Registrierung |
| D | **C plus Deep-Link als Klickziel auf dem Preis** | ja | nur als Link | ja | gering | wie C + wenig |

**Variante B ist gestrichen** — nicht aus Vorsicht, sondern weil sie technisch nicht funktioniert
(kein CORS) und nur mit dauerhaft kompromittiertem `client_secret` im ausgelieferten JS-Bundle
denkbar wäre.

**Empfehlung: Variante D.**
Sie erreicht das formulierte Ziel („günstigster Sofortkauf- und Auktionspreis im Dashboard")
vollständig.
Aus Nutzersicht ändert sich gegenüber der ursprünglichen Idee **nichts**:
Knopf neben dem Titel, Abfrage im Hintergrund, Preise erscheinen in der Ansicht.
Es ändert sich nur, wer den Request nach draußen schickt — und genau das ist auch aus
Datenschutzsicht die bessere Variante (Abschnitt 5).

**Entscheidung des Auftraggebers erforderlich**, bevor Phase 1 beginnt.

## 5. Sicherheitsanforderungen

Verbindlich für die Umsetzung. Jeder Punkt ist eine Anforderung, kein Hinweis.

### 5.1 Zugangsdaten
- `client_id`/`client_secret` liegen **ausschließlich serverseitig** und kommen aus der Umgebung
  (`EBAY_CLIENT_ID`/`EBAY_CLIENT_SECRET`), dokumentiert in `.env.example` —
  niemals im committeten `application.properties`, niemals im Frontend-Bundle.
- Feature-Flag `ebay.enabled` (Default `false`), analog `tmdb.enabled`:
  ohne gesetzte Zugangsdaten ist das Feature aus und der Knopf wird nicht gerendert.
- Das `@ConfigurationProperties`-Record braucht ein **maskierendes `toString()`** —
  Records generieren sonst ein `toString()`, das das Secret in jeden Log-Eintrag schreibt,
  der die Properties ausgibt.
- Das App-Token wird nur im Speicher gehalten (mit Ablaufzeitpunkt und Sicherheitsmarge),
  **nie geloggt und nie an den Client ausgeliefert**.

### 5.2 Der Endpunkt darf kein offener eBay-Proxy werden
- `GET /api/titles/{imdbId}/offers` nimmt **keinen freien Suchtext** entgegen, sondern nur eine
  IMDb-ID.
  Den eBay-Suchbegriff bildet der Server aus seinen **eigenen** Daten (Titelname, ggf. deutscher
  Titel aus dem Metadaten-Cache).
- Die ID muss auf der **Watchlist des anfragenden Benutzers** liegen
  (Prüfung über den bestehenden `WatchlistCatalogPort.isOnWatchlist(userId, imdbId)`),
  sonst `404`.
  Andernfalls wäre der Endpunkt eine kostenlose, authentifizierte eBay-Suche auf Kosten unseres
  Tageskontingents.
- Authentifizierung wie bei allen `GET /api/**`: angemeldeter Benutzer.

### 5.3 Missbrauch und Kontingent
- **Globaler Cache pro Titel** (nicht pro Benutzer), TTL-basiert wie der werstreamt.es-Cache.
  Zwei getrennte TTLs: Auktionsgebote sind volatil (Vorschlag 30–60 min),
  Sofortkaufpreise träge (Vorschlag 12–24 h).
- **In-Flight-Deduplizierung**: parallele Anfragen zum selben Titel lösen einen einzigen
  Upstream-Call aus.
- **Ausgehende Drosselung** über den bestehenden `RateLimiter` (eigene Instanz, eigene Properties)
  — dasselbe Muster wie IMDb/werstreamt.es.
- **Tagesbudget-Zähler mit hartem Stopp** unterhalb der 5.000 Calls;
  bei Erschöpfung liefert der Endpunkt den Cache-Stand bzw. „derzeit nicht verfügbar",
  statt weiter zu rufen.
- **Per-User-Limit auf dem Endpunkt** (neu für dieses Projekt, siehe Abschnitt 2):
  ein Skript mit gültigem Session-Cookie kann sonst 2N Upstream-Calls in Sekunden auslösen.
  Clientseitiges Limiting ist dagegen wirkungslos und zählt nicht als Schutz.
- **Kein „alle Titel laden"-Knopf** und kein automatisches Vorladen beim Seitenaufbau
  (siehe Abschnitt 9).

### 5.4 Fremdinhalte im Frontend
- Das API-DTO wird auf das Nötigste eingedampft:
  `{ buyNow: { amountCents, currency, url } | null, auction: { amountCents, currency, url } | null, fetchedAt }`.
  **Keine** Angebotstitel, Verkäufernamen, Beschreibungen oder Bild-URLs durchreichen —
  je weniger nutzergenerierter Fremdinhalt ankommt, desto kleiner die XSS-Fläche.
- Angular escaped Interpolation und Property-Bindings automatisch und saniert `[href]`/`[src]`
  (blockt `javascript:`).
  **Nicht** geschützt sind `innerHTML`-Bindings und alles hinter `bypassSecurityTrust*` —
  beides ist für dieses Feature ausdrücklich verboten.
- Die Angebots-URL wird **serverseitig gegen eine Host-Allowlist** geprüft
  (`ebay.de`, `ebay.com`), bevor sie gespeichert oder ausgeliefert wird.
  Rendering wie bestehend als `<a target="_blank" rel="noopener">`.
- Falls später doch Angebotsbilder gezeigt werden sollen: nach dem Poster-Muster serverseitig
  holen und same-origin ausliefern, nicht per `[src]` auf die eBay-CDN verlinken.

### 5.5 Content-Security-Policy
- Mit Variante C/D bleibt alles same-origin — eine strikte CSP (`default-src 'self'`) ist möglich,
  **ohne** irgendetwas für eBay zu öffnen.
  (Variante B hätte `connect-src api.ebay.com` und `img-src *.ebayimg.com` erzwungen.)
- Empfehlung unabhängig von diesem Feature: CSP in `SecurityConfig` einführen —
  aktuell fehlt der Backstop gegen XSS vollständig.
  Als **eigener Pfadfinder-Commit**, getrennt vom Feature, da Angular Material inline-Styles
  erzeugt und die genaue `style-src`-Direktive erst verifiziert werden muss.

### 5.6 Datenschutz und Nutzungsbedingungen
- Client-Direktcalls (Variante B) hätten IP-Adresse und implizit das Filminteresse jedes Benutzers
  an einen Drittlandempfänger gesendet — ohne jede Consent-Mechanik in der App.
  Mit dem Backend-Proxy entfällt dieser Punkt vollständig: eBay sieht nur die Server-IP.
- „Günstigster Preis für Film X" ist **nicht personenbezogen** → globaler Cache, kein Bezug zum
  Benutzerkonto. Damit bleibt auch die Watchlist-Zuordnung im eigenen Haus.
- Das API License Agreement verlangt die **Löschung von eBay-Daten binnen 30 Tagen** nach
  Vertragsende und verbietet AI-Training mit eBay-Daten.
  Konsequenz für uns: TTLs weit unter 30 Tagen (ohnehin nötig) plus ein Aufräumjob, der alte
  Cache-Zeilen löscht.
- Scraping der Website ist untersagt; die API ist der sanktionierte Weg.
- Der angezeigte Preis bekommt einen **„Stand: …"-Zeitstempel** — bei Auktionen ist ein
  Cache-Wert sonst irreführend.

## 6. Offene Entscheidungen

1. **Variante** (Abschnitt 4) — Empfehlung D, Freigabe steht aus. Blockiert Phase 1.
2. **Suchbegriff:** deutscher Titel (nur verfügbar, wenn der Metadaten-Cache ihn hat) oder
   Originalname; mit oder ohne Jahr; Kategoriefilter auf „DVDs & Blu-ray Discs".
   Ein Jahr im Suchbegriff senkt bei eBay den Recall spürbar (AND-Verknüpfung der Keywords,
   Editionsjahre weichen vom Erscheinungsjahr ab).
   Die Trefferqualität ist das eigentliche Produktrisiko dieses Features:
   „Heat" findet ohne Kategoriefilter vor allem Heizungszubehör.
3. **Preisdefinition:** Artikelpreis oder Artikelpreis + Versand?
   Vorschlag v1: reiner Artikelpreis, Versand ignoriert, im UI kenntlich gemacht.
4. **Marktplatz:** fest `EBAY_DE` oder abgeleitet aus `UserPrefsStore.language()`.
5. **Scope:** nur Dashboard oder überall, wo `TitleCell`/`TitleTile` gerendert wird
   (Provider-Seiten nutzen dieselben Komponenten).
   Vorschlag: eigene Spalte/Chip nur auf dem Dashboard, damit die Provider-Seiten unberührt
   bleiben.
6. **Anzeigeort:** eigene Tabellenspalte, Chip auf der Poster-Kachel, oder Aufklapp-Detail.
   Beides muss in Tabelle **und** Grid funktionieren.
7. **Bounded Context:** neuer Kontext `purchaseoffers` (eigene ArchUnit-Isolationsregel, ADR-pflichtig)
   oder Einbau in `streamingavailability` (das bereits Preise für Kauf-/Leih-Angebote kennt,
   siehe `PaidEntry.price`).
   Vorschlag: neuer Kontext — eBay-Angebote sind eine eigene Fähigkeit mit eigener Cache-Tabelle
   und eigenem Fremdsystem.
8. **EPN-Affiliate-Parameter** an ausgehenden Links: Geschäftsentscheidung, nicht Technik.

## 7. Phasenplan

```
Phase 0 (eBay-Account, ADR)          ──► Voraussetzung für alles
Phase 1 (Backend-Kontext + Adapter)  ──► Voraussetzung für Phase 2
Phase 2 (Frontend: Knopf + Anzeige)  ◄── braucht Phase 1
Phase 3 (CSP)                        ──► unabhängig, eigener Commit
```

### Phase 0 — Vorbereitung (kein Code)
- eBay-Developer-Account anlegen, Production-Keyset erzeugen, Kontingent prüfen.
- **ADR-0017** „eBay-Preise über Backend-Adapter statt Client-Direktabfrage" schreiben
  (via `adr`-Skill): hält fest, *warum* die Client-Variante ausscheidet (CORS, Secret, Kontingent,
  DSGVO) — sonst wird die Frage in sechs Monaten neu aufgerollt.
- Entscheidungen aus Abschnitt 6 treffen.

### Phase 1 — Backend
Neuer Bounded Context `purchaseoffers` unter `tech.dobler.where2stream`, Aufbau nach ADR-0014:

- `domain`: `OfferPrice` (Betrag + Währung als Value Object, ADR-0009), `TitleOffers`
  (günstigster Sofortkauf, günstigstes laufendes Gebot, Abrufzeitpunkt).
- `port/out`: `PurchaseOfferSource` — die Schnittstelle, die der Adapter erfüllt.
- `adapter/out/ebay`:
  - `EbayProperties` (`@ConfigurationProperties("ebay")`): `enabled`, `clientId`, `clientSecret`,
    `marketplaceId`, `categoryId`, `rateLimit`, TTLs, Tagesbudget — mit maskierendem `toString()`.
  - `EbayTokenProvider`: Client-Credentials-Token, im Speicher gecacht bis kurz vor Ablauf.
  - `EbayBrowseSource`: zwei Suchen je Titel (`FIXED_PRICE` / `AUCTION`), Minimum bestimmen.
    Parsing und Minimum-Auswahl als **statische, netzwerkfreie Methoden** (wie
    `ImdbSuggestionSource.parse`), damit sie ohne Netz testbar sind.
    Nutzt `HttpClientFactory`, `OutboundHttpClients.USER_AGENT` und `RateLimiter`.
  - Fehlerverhalten wie überall im Projekt: loggen und auf „kein Ergebnis" degradieren,
    nie den Request des Benutzers mit einer Exception beenden.
- `adapter/out/persistence`: Cache-Entity + Repository + **Liquibase-Changelog** für die neue
  Tabelle (globaler Cache, Schlüssel `imdb_id`, beide Preise, Währung, URLs, Abrufzeitpunkt,
  Negativ-Markierung für „nichts gefunden").
  Schema-Hoheit liegt bei Liquibase (`ddl-auto=none`).
- `application`: `TitleOfferService` — Cache-First, TTL-Prüfung, In-Flight-Deduplizierung,
  Budgetbremse.
- `adapter/in/api`: `PurchaseOfferApiController`, `GET /api/titles/{imdbId}/offers`,
  mit den Prüfungen aus 5.2.
- `ArchitectureTest` (ArchUnit) um die Isolationsregel für den neuen Kontext erweitern —
  sonst schlägt der bestehende Test nicht an, aber der Kontext ist ungeschützt.
- Tests: Parsing/Minimum-Auswahl netzwerkfrei, Controller mit MockMvc
  (inkl. „ID nicht auf meiner Watchlist → 404"), TTL-/Budget-Verhalten.
  AssertJ + Mockito (ADR-0005), Mehrfeld-Prüfungen als **eine** `extracting(...)`-Assertion
  (Skill `consolidate-test-assertions`).

### Phase 2 — Frontend
- `core/api/offers-api.ts`: `OffersApi.get(imdbId)` — dünn, wie die übrigen `*-api.ts`.
- `core/offers-store.ts`: Zustand je `imdbId` (`idle | loading | loaded | error`),
  Deduplizierung, **kein** automatischer Abruf beim Seitenaufbau.
  Muster: `SeenStore`.
- `shared/offer-prices/offer-prices.ts`: dumme Komponente — Knopf im Ruhezustand,
  danach die zwei Preise mit „Stand"-Zeitstempel.
  Ladezustand als **Skeleton-Bar** (`.skeleton-bar--narrow`), nicht als Spinner
  (Projektkonvention, siehe `CLAUDE.md`).
- Einbau in `CatalogTable` (neue Spalte) und `TitleGrid`/`TitleTile` (Chip),
  gesteuert über den in Abschnitt 6.5 zu entscheidenden Scope.
- i18n: neuer Block in `src/i18n/de.json` **und** `en.json`, Schlüssel parallel halten.
- Tests (Vitest, ADR-0004): Store (Dedup, Fehlerpfad), Komponente (Rendering, `rel="noopener"`,
  kein `innerHTML`), API-Service.
- `npm run lint` + `npm run test:ci` als Gate.

### Phase 3 — Content-Security-Policy (unabhängig)
- `.headers(...)`-Customizer in `SecurityConfig` mit strikter Policy;
  `style-src`-Bedarf von Angular Material vorher verifizieren.
- Eigener Commit, getrennt vom Feature (Pfadfinder-Konvention).

## 8. Nicht verifiziert

Ehrlich offen — vor der Umsetzung zu klären, nicht zu raten:

- CORS-Verhalten der **Datenendpunkte** der Browse API (belegt ist nur das Fehlen von CORS am
  OAuth-Endpunkt). Für Variante C/D irrelevant, für die Bewertung von B nur mittelbar.
- Ob `sort=price` mit `buyingOptions:{AUCTION}` kombinierbar ist.
  Falls nicht, muss das Minimum im Backend über eine begrenzte Trefferliste selbst bestimmt werden.
- Genaue Caching-Fristen für volatile Gebotsdaten im aktuellen License Agreement
  (belegt ist nur die 30-Tage-Löschklausel nach Vertragsende).
- Pflichten des eBay Partner Network bei kommerzieller Nutzung (für ein privates Projekt
  vermutlich gegenstandslos).
- Die eBay-Kategorie-ID für „DVDs & Blu-ray Discs" auf `ebay.de` — vor dem Commit an einer echten
  Suche verifizieren.

## 9. Nicht-Ziele

- Kein Kaufabschluss, keine Gebotsabgabe, keine eBay-Anmeldung des Benutzers aus w2s heraus.
- Keine Preishistorie, keine Preisalarme in dieser Ausbaustufe.
- Keine Verkäufer-, Versand- oder Zustandsbewertung — nur die zwei geforderten Preise.
- **Kein Massen-Abruf** („Preise für alle Titel laden") und kein Vorladen im Hintergrund:
  das Tagesbudget von 2.500 Titelabfragen für alle Benutzer zusammen lässt das nicht zu.
  Der Abruf bleibt bewusst an eine bewusste Nutzeraktion gebunden.

## 10. Änderungshistorie

- **2026-09-05** — Entwurf v1: Zielbild, Ist-Zustand, Varianten, offene Punkte.
- **2026-09-05** — Entwurf v2: Machbarkeit geklärt (Client-Direktabfrage scheidet aus;
  Auktionsgebote sind über `currentBidPrice` abrufbar; 5.000 Calls/Tag als harte Grenze),
  Sicherheitsanforderungen ausformuliert, Phasenplan ergänzt,
  nicht verifizierte Punkte benannt.
