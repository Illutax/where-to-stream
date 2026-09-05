# Plan: eBay-Preisabfrage pro Titel im Dashboard

Ziel dieses Dokuments ist ein Umsetzungsplan für die eBay-Anbindung von w2s.
Es ist bewusst so geschrieben, dass eine **andere Claude-Code-Session ohne Vorwissen aus dem
Ursprungsgespräch** direkt damit arbeiten kann: jeder Schritt nennt die konkrete Datei, die
betroffenen Klassen/Komponenten und was sich ändert.

**Status: Entwurf v3, in Arbeit.**
Machbarkeit und Sicherheitslage sind geklärt (Abschnitte 3–5), die Empfehlung steht (Abschnitt 4.2).
Offen ist der Spike aus Phase 0 — er entscheidet, ob die empfohlene Variante vom Zielhost aus
überhaupt funktioniert — sowie die Detailentscheidungen aus Abschnitt 6.

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
| C | Backend-Adapter auf der **Browse API** + Cache | ja | nein | ja | gering | 1–3 Tage + eBay-Registrierung |
| D | C plus Deep-Link als Klickziel auf dem Preis | ja | nur als Link | ja | gering | wie C + wenig |
| E | Backend **scraped** die eBay-Suchergebnisseite, Anzeige flüchtig im Client | ja | nein | ja, aber blockierungsgefährdet | gering (keine Secrets) | 1–2 Tage, kein Account |

**Variante B ist gestrichen** — nicht aus Vorsicht, sondern weil sie technisch nicht funktioniert
(kein CORS) und nur mit dauerhaft kompromittiertem `client_secret` im ausgelieferten JS-Bundle
denkbar wäre.

### 4.1 Variante E im Vergleich zu C/D

Variante E verzichtet auf die API und liest die öffentliche Suchergebnisseite
(`ebay.de/sch/i.html?_nkw=…`) mit jsoup aus — dasselbe Verfahren, mit dem w2s heute schon
werstreamt.es ausliest.
Der Auftraggeber hat entschieden, den damit verbundenen **Verstoß gegen die eBay-Nutzungsbedingungen
bewusst in Kauf zu nehmen** (siehe 5.7).

Was für E spricht:

- **Kein eBay-Developer-Account, kein `client_id`/`client_secret`, kein OAuth-Token.**
  Der gesamte Abschnitt „Zugangsdaten" der API-Variante entfällt —
  es gibt schlicht kein Geheimnis, das leaken kann.
  Sicherheitstechnisch ist E in diesem Punkt **besser** als C.
- **Das 5.000-Calls-Tagesbudget entfällt** und damit die härteste Randbedingung des Plans.
  Die Begrenzung wird von einer harten Quote zu einer Frage der Höflichkeit (Rate-Limit).
- **Ein Request statt zwei je Titel** ist möglich: Sofortkauf- und Auktionsangebote lassen sich
  über URL-Parameter trennen (`LH_BIN=1` / `LH_Auction=1`) — oder man liest eine gemischte,
  preisaufsteigend sortierte Ergebnisseite und trennt beim Parsen.
- **Das Projekt hat den Bauplan bereits.**
  `WerStreamtEsSource` liefert das komplette Muster: jsoup, `ConnectionFactory`-Seam für
  netzwerkfreie Tests, statische package-private Parse-Methoden, `RateLimiter`,
  Degradation auf leere Liste bei `HttpStatusException`.
  Auch der Chrome-`User-Agent` (`OutboundHttpClients.USER_AGENT`) ist etablierte Praxis —
  E führt **keine neue Verhaltensweise** ein, die das Projekt nicht ohnehin schon zeigt.

Was gegen E spricht — und das ist ernster als die AGB-Frage:

- **Es kann sein, dass es von der Produktions-IP aus schlicht nicht funktioniert.**
  Dieses Projekt hat den Fall bereits einmal erlebt und im README dokumentiert:
  `www.imdb.com` liefert an Rechenzentrums-IPs ein leeres `202`, weshalb die Poster-Beschaffung
  dort auf die GraphQL-API ausweichen musste statt die Titelseite zu scrapen.
  eBays Anti-Bot-Maßnahmen gelten als mindestens ebenso aggressiv.
  **Auf dem Entwicklungsrechner wird es funktionieren und auf dem Server möglicherweise nicht** —
  das ist die gefährlichste Eigenschaft dieser Variante, weil sie falsche Sicherheit erzeugt.
  Deshalb steht in Phase 0 ein Spike, der genau das **vom Zielhost aus** prüft, bevor Code
  entsteht.
- **Brüchigkeit.** Markup-Änderungen brechen das Parsing jederzeit und lautlos.
  Die API hat einen Vertrag, die HTML-Seite nicht.
- **Kein Auktions-Enddatum, keine Gebotszahl** ohne Detailseiten-Abruf —
  die Suchergebnisseite zeigt beides nur eingeschränkt. Für „günstigstes laufendes Gebot" reicht
  sie, für mehr nicht.

### 4.2 Empfehlung

**Variante E als Standardquelle, hinter einem Port — mit C als Ausweichadapter.**

Der entscheidende Punkt ist, dass diese Wahl **nicht endgültig sein muss**.
Die Architektur des Projekts macht sie umkehrbar, und es gibt dafür bereits einen Präzedenzfall:
Poster sind über `PosterSource` austauschbar (IMDb per Default, TMDB hinter `tmdb.enabled`).
Dasselbe hier:

- `PurchaseOfferSource` ist der Port (Abschnitt 7, Phase 1).
- `EbayScrapeSource` ist der Adapter für Variante E — Standard, ohne Konfiguration lauffähig.
- `EbayBrowseApiSource` kann später als zweiter Adapter hinter einem Flag (`ebay.api.enabled`)
  dazukommen, falls das Scraping blockiert wird oder die AGB-Frage neu bewertet wird.

Anwendungsschicht, API-Endpunkt, DTO und das gesamte Frontend sind von der Wahl **nicht** betroffen.
Wird E blockiert, kostet der Wechsel eine Adapter-Klasse, nicht das Feature.
Damit ist der Weg mit dem geringsten Vorab-Aufwand (kein Account, kein Freigabeprozess) auch der
Weg, der am wenigsten kostet, wenn er scheitert.

## 5. Sicherheitsanforderungen

Verbindlich für die Umsetzung. Jeder Punkt ist eine Anforderung, kein Hinweis.
5.1–5.3 gelten unabhängig von der Quellenvariante, 5.4 nur für C/D, 5.5 nur für E.

### 5.1 Der Endpunkt darf kein offener eBay-Proxy werden
- `GET /api/titles/{imdbId}/offers` nimmt **keinen freien Suchtext** entgegen, sondern nur eine
  IMDb-ID.
  Den eBay-Suchbegriff bildet der Server aus seinen **eigenen** Daten (Titelname, ggf. deutscher
  Titel aus dem Metadaten-Cache).
- Die ID muss auf der **Watchlist des anfragenden Benutzers** liegen
  (Prüfung über den bestehenden `WatchlistCatalogPort.isOnWatchlist(userId, imdbId)`),
  sonst `404`.
- Authentifizierung wie bei allen `GET /api/**`: angemeldeter Benutzer.
- Bei Variante E steht dabei nicht ein Kontingent auf dem Spiel, sondern die **Erreichbarkeit
  unserer Server-IP** — der Schutz wird dadurch wichtiger, nicht unwichtiger.

### 5.2 Missbrauch und Lastbegrenzung
- **Ausgehende Drosselung** über den bestehenden `RateLimiter` (eigene Instanz, eigene Properties).
  Für Scraping bewusst konservativ ansetzen (Vorschlag: ≤ 1 Request/Sekunde, wie werstreamt.es).
- **In-Flight-Deduplizierung**: parallele Anfragen zum selben Titel lösen einen einzigen
  Upstream-Request aus.
- **Per-User-Limit auf dem Endpunkt** (neu für dieses Projekt, siehe Abschnitt 2):
  ein Skript mit gültigem Session-Cookie kann sonst N Upstream-Requests in Sekunden auslösen.
  Clientseitiges Limiting ist dagegen wirkungslos und zählt nicht als Schutz.
- **Kein „alle Titel laden"-Knopf** und kein automatisches Vorladen beim Seitenaufbau
  (siehe Abschnitt 9).

### 5.3 Fremdinhalte im Frontend
- Das API-DTO wird auf das Nötigste eingedampft:
  `{ buyNow: { amountCents, currency, url } | null, auction: { amountCents, currency, url } | null, fetchedAt }`.
  **Keine** Angebotstitel, Verkäufernamen, Beschreibungen oder Bild-URLs durchreichen —
  je weniger nutzergenerierter Fremdinhalt ankommt, desto kleiner die XSS-Fläche.
  Bei einer gescrapten Seite ist das zusätzlich ein Robustheitsgewinn: was nicht geparst wird,
  kann auch nicht kaputtgehen.
- Angular escaped Interpolation und Property-Bindings automatisch und saniert `[href]`/`[src]`
  (blockt `javascript:`).
  **Nicht** geschützt sind `innerHTML`-Bindings und alles hinter `bypassSecurityTrust*` —
  beides ist für dieses Feature ausdrücklich verboten.
- Die Angebots-URL wird **serverseitig gegen eine Host-Allowlist** geprüft
  (`ebay.de`, `ebay.com`), bevor sie ausgeliefert wird — bei einer gescrapten Seite steht die URL
  in fremdem Markup und ist damit Eingabe, nicht Ausgabe.
  Rendering wie bestehend als `<a target="_blank" rel="noopener">`.
- Beträge werden serverseitig in einen numerischen Typ geparst und als Zahl + Währungscode
  ausgeliefert — **kein** durchgereichter Preis-String aus dem HTML.

### 5.4 Zugangsdaten — nur Variante C/D
- `client_id`/`client_secret` ausschließlich serverseitig aus der Umgebung
  (`EBAY_CLIENT_ID`/`EBAY_CLIENT_SECRET`), dokumentiert in `.env.example`.
- Feature-Flag `ebay.api.enabled` (Default `false`), analog `tmdb.enabled`.
- Das `@ConfigurationProperties`-Record braucht ein **maskierendes `toString()`** —
  Records generieren sonst ein `toString()`, das das Secret in jeden Log-Eintrag schreibt.
- App-Token nur im Speicher, nie geloggt, nie an den Client ausgeliefert.

### 5.5 Blockierungsrisiko — nur Variante E
- **Fehlerverhalten:** HTTP 403/429/Captcha-Seite führen zu „derzeit nicht verfügbar" im UI,
  geloggt auf `warn` — niemals zu einer Exception im Benutzer-Request
  (Muster: `WerStreamtEsSource` fängt `HttpStatusException` und degradiert auf leere Liste).
- **Circuit Breaker:** nach wiederholten Blockade-Antworten für eine konfigurierbare Zeit gar
  nicht mehr rufen. Weiterhämmern verwandelt eine temporäre Sperre in eine dauerhafte.
- **Keine Eskalation der Verschleierung.** Der vorhandene `OutboundHttpClients.USER_AGENT` und ein
  striktes Rate-Limit sind die Grenze.
  Proxy-Rotation, Captcha-Löser oder Headless-Browser-Fingerprinting sind ausdrücklich
  **nicht** Teil dieses Plans: der Wartungsaufwand explodiert, die Erfolgsquote bleibt instabil,
  und es verschiebt das Vorhaben von „unbequem" zu „aktiv umgangen".
  Wird blockiert, ist der geplante Ausweg der API-Adapter (4.2), nicht ein Wettrüsten.
- **Monitoring:** die Trefferquote (Anteil Abrufe mit mindestens einem Preis) muss im Log sichtbar
  sein. Bricht das Parsing durch eine Markup-Änderung, sieht man sonst nur „keine Angebote" —
  ununterscheidbar von „es gibt wirklich keine".
- Die Parse-Methoden sind **statisch und netzwerkfrei** und werden gegen ein eingechecktes
  HTML-Fixture getestet, damit ein Markup-Bruch beim nächsten Fixture-Update auffällt.

### 5.6 Datenhaltung: was „flüchtig" konkret heißt
- „Flüchtig im Client anzeigen" löst die Frage der **Speicherung**, nicht die der **Last**.
  Ohne jeden Cache erzeugt jeder Knopfdruck einen Live-Request — genau das beschleunigt eine
  Blockade.
- Empfehlung: **kein DB-Persist, aber ein kurzlebiger In-Memory-Cache** im Server
  (TTL im Minutenbereich für Auktionen, Stundenbereich für Sofortkauf).
  Damit bleibt es „flüchtig" im gemeinten Sinn (nichts wird dauerhaft gespeichert, kein
  Liquibase-Changelog, keine neue Tabelle, keine Löschfristen), aber wiederholte Klicks auf
  denselben Titel erzeugen keinen neuen Fremdzugriff.
  Der Cache ist **global** pro Titel, nicht pro Benutzer.
- „Günstigster Preis für Film X" ist nicht personenbezogen.
  Da nichts persistiert wird, entfallen Löschfristen und die Frage nach personenbezogenen Daten
  ohnehin.
- Der angezeigte Preis bekommt einen **„Stand: …"-Zeitstempel** — bei Auktionen ist ein
  Cache-Wert sonst irreführend.
- Ein Neustart der Anwendung leert den Cache. Das ist akzeptabel und ausdrücklich gewollt.

### 5.7 Nutzungsbedingungen — bewusste Entscheidung
- Die eBay-Nutzungsbedingungen untersagen das automatisierte Auslesen der Website;
  die API ist der vorgesehene Weg.
  Der Auftraggeber hat entschieden, das für dieses private, nicht-kommerzielle Projekt
  **wissentlich in Kauf zu nehmen**.
- Diese Entscheidung wird im ADR **als solche dokumentiert** — mit Begründung und mit dem
  benannten Ausweg (API-Adapter).
  Ein bewusst eingegangenes Risiko, das aufgeschrieben ist, ist etwas anderes als eines, das
  jemand später im Code entdeckt.
- Realistische Konsequenz ist eine **IP-Sperre**, nicht ein Rechtsstreit:
  es gibt kein eBay-Konto, das gesperrt werden könnte, und keine kommerzielle Nutzung.
  (Das ist eine Einschätzung der praktischen Risikolage, keine Rechtsberatung.)
- Das Projekt scraped mit werstreamt.es bereits eine fremde Website — die Entscheidung ist
  konsistent mit dem, was w2s ohnehin tut, und nicht ein neuer Präzedenzfall.

## 6. Offene Entscheidungen

1. **Variante** (Abschnitt 4) — Empfehlung E hinter dem Port, C als späterer Ausweichadapter.
   Blockiert Phase 1.
2. **Suchbegriff:** deutscher Titel (nur verfügbar, wenn der Metadaten-Cache ihn hat) oder
   Originalname; mit oder ohne Jahr; Kategoriefilter auf „DVDs & Blu-ray Discs" (`_sacat`).
   Die Trefferqualität ist das eigentliche Produktrisiko dieses Features:
   „Heat" findet ohne Kategoriefilter vor allem Heizungszubehör.
3. **Preisdefinition:** Artikelpreis oder Artikelpreis + Versand?
   Beim Scraping ist das sogar einfacher als über die API, weil die Ergebnisseite die Sortierung
   „Preis + Versand: niedrigste zuerst" (`_sop=15`) selbst anbietet.
   Vorschlag v1: reiner Artikelpreis, Versand ignoriert, im UI kenntlich gemacht.
4. **Marktplatz:** fest `ebay.de` oder abgeleitet aus `UserPrefsStore.language()`.
5. **Scope:** nur Dashboard oder überall, wo `TitleCell`/`TitleTile` gerendert wird
   (Provider-Seiten nutzen dieselben Komponenten).
   Vorschlag: nur Dashboard.
6. **Anzeigeort:** eigene Tabellenspalte, Chip auf der Poster-Kachel, oder Aufklapp-Detail.
   Beides muss in Tabelle **und** Grid funktionieren.
7. **Bounded Context:** neuer Kontext `purchaseoffers` (eigene ArchUnit-Isolationsregel, ADR-pflichtig)
   oder Einbau in `streamingavailability` (das über `PaidEntry.price` bereits Kaufpreise kennt).
   Vorschlag: neuer Kontext.
8. **EPN-Affiliate-Parameter** an ausgehenden Links: bei Variante E ohnehin gegenstandslos.

## 7. Phasenplan

```
Phase 0 (Spike vom Zielhost + ADR)   ──► Voraussetzung; kann das Vorhaben stoppen
Phase 1 (Backend: Port + Scrape-Adapter) ──► Voraussetzung für Phase 2
Phase 2 (Frontend: Knopf + Anzeige)  ◄── braucht Phase 1
Phase 3 (CSP)                        ──► unabhängig, eigener Commit
```

### Phase 0 — Spike und Entscheidung (fast kein Code)
- **Zuerst und wichtigstes:** von der **Produktionsmaschine** aus (nicht vom Entwicklungsrechner!)
  die eBay-Suchergebnisseite mit dem projekteigenen User-Agent abrufen und prüfen,
  ob echtes Ergebnis-Markup zurückkommt — oder Captcha/403/leere Seite.
  Das ist eine Frage von Minuten und entscheidet über die gesamte Variante.
  Fällt der Spike negativ aus, ist Variante C/D der Weg und Phase 1 sieht anders aus.
- Aus derselben abgerufenen Seite die **Selektoren und URL-Parameter** verifizieren:
  `_nkw`, `_sacat` (Kategorie DVDs/Blu-ray), `LH_BIN=1`, `LH_Auction=1`, `_sop` (Sortierung),
  und wie Sofortkaufpreis und aktuelles Gebot im Markup unterscheidbar sind.
  Die Seite als **Test-Fixture** einchecken.
- **ADR-0017** „eBay-Preise über gescrapte Suchergebnisseite hinter austauschbarem Port"
  (via `adr`-Skill): hält fest, warum die Client-Variante ausscheidet (CORS, Secret),
  warum Scraping statt API gewählt wurde, dass der AGB-Verstoß bewusst eingegangen wird,
  und was der Ausweg ist.
- Entscheidungen aus Abschnitt 6 treffen.

### Phase 1 — Backend
Neuer Bounded Context `purchaseoffers` unter `tech.dobler.where2stream`, Aufbau nach ADR-0014:

- `domain`: `OfferPrice` (Betrag + Währung als Value Object, ADR-0009), `TitleOffers`
  (günstigster Sofortkauf, günstigstes laufendes Gebot, Abrufzeitpunkt).
- `port/out`: `PurchaseOfferSource` — die austauschbare Schnittstelle (siehe 4.2).
- `adapter/out/ebay`:
  - `EbayProperties` (`@ConfigurationProperties("ebay")`): Basis-URL, Kategorie, Marktplatz,
    `rateLimit`, Cache-TTLs, Circuit-Breaker-Schwellen.
  - `EbayScrapeSource implements PurchaseOfferSource`: jsoup, `ConnectionFactory`-Seam,
    `RateLimiter`, Degradation auf „kein Ergebnis" — Vorbild ist `WerStreamtEsSource`
    Zeile für Zeile.
    Parsing als **statische, package-private Methoden** gegen das Fixture aus Phase 0 testbar.
- `application`: `TitleOfferService` — In-Memory-TTL-Cache, In-Flight-Deduplizierung,
  Circuit Breaker.
- `adapter/in/api`: `PurchaseOfferApiController`, `GET /api/titles/{imdbId}/offers`,
  mit den Prüfungen aus 5.1.
- **Kein** Liquibase-Changelog, **keine** neue Tabelle, **keine** Entity — der Cache ist flüchtig.
- `ArchitectureTest` (ArchUnit) um die Isolationsregel für den neuen Kontext erweitern.
- Tests: Parsing gegen Fixture (netzwerkfrei), Controller mit MockMvc
  (inkl. „ID nicht auf meiner Watchlist → 404"), TTL-/Circuit-Breaker-Verhalten.
  AssertJ + Mockito (ADR-0005), Mehrfeld-Prüfungen als **eine** `extracting(...)`-Assertion
  (Skill `consolidate-test-assertions`).

### Phase 2 — Frontend
- `core/api/offers-api.ts`: `OffersApi.get(imdbId)` — dünn, wie die übrigen `*-api.ts`.
- `core/offers-store.ts`: Zustand je `imdbId` (`idle | loading | loaded | error`),
  Deduplizierung, **kein** automatischer Abruf beim Seitenaufbau. Muster: `SeenStore`.
- `shared/offer-prices/offer-prices.ts`: dumme Komponente — Knopf im Ruhezustand,
  danach die zwei Preise mit „Stand"-Zeitstempel, plus Zustand „derzeit nicht verfügbar".
  Ladezustand als **Skeleton-Bar** (`.skeleton-bar--narrow`), nicht als Spinner
  (Projektkonvention, siehe `CLAUDE.md`).
- Einbau in `CatalogTable` (neue Spalte) und `TitleGrid`/`TitleTile` (Chip),
  gesteuert über den in 6.5 zu entscheidenden Scope.
- i18n: neuer Block in `src/i18n/de.json` **und** `en.json`, Schlüssel parallel halten.
- Tests (Vitest, ADR-0004): Store (Dedup, Fehlerpfad), Komponente (Rendering, `rel="noopener"`,
  kein `innerHTML`), API-Service.
- `npm run lint` + `npm run test:ci` als Gate.

### Phase 3 — Content-Security-Policy (unabhängig)
- `.headers(...)`-Customizer in `SecurityConfig` mit strikter Policy (`default-src 'self'`);
  mit Variante E bleibt alles same-origin, es muss **nichts** für eBay geöffnet werden.
- `style-src`-Bedarf von Angular Material vorher verifizieren.
- Eigener Commit, getrennt vom Feature (Pfadfinder-Konvention).

## 8. Nicht verifiziert

Ehrlich offen — vor der Umsetzung zu klären, nicht zu raten:

- **Ob eBay Requests vom Zielhost überhaupt beantwortet** (Phase-0-Spike).
  Der wichtigste offene Punkt des gesamten Plans.
- Die aktuellen **CSS-Selektoren** der eBay-Suchergebnisseite und ob sich Sofortkauf- und
  Auktionsangebote dort zuverlässig unterscheiden lassen.
- Die URL-Parameter `_sacat` (Kategorie DVDs/Blu-ray auf `ebay.de`), `LH_BIN`, `LH_Auction`,
  `_sop` — plausibel, aber unverifiziert.
- Ob ein gemischter Abruf (eine Seite, preisaufsteigend) beide Preise liefert oder zwei getrennte
  Abrufe nötig sind.
- Für den Ausweichfall C: CORS-Verhalten der Browse-Datenendpunkte, Kombinierbarkeit von
  `sort=price` mit `buyingOptions:{AUCTION}`.

## 9. Nicht-Ziele

- Kein Kaufabschluss, keine Gebotsabgabe, keine eBay-Anmeldung des Benutzers aus w2s heraus.
- Keine Preishistorie, keine Preisalarme in dieser Ausbaustufe.
- Keine Verkäufer-, Versand- oder Zustandsbewertung — nur die zwei geforderten Preise.
- **Kein Massen-Abruf** („Preise für alle Titel laden") und kein Vorladen im Hintergrund.
  Der Abruf bleibt an eine bewusste Nutzeraktion gebunden — bei Variante E, weil jeder Request
  das Blockierungsrisiko erhöht.
- **Kein Wettrüsten gegen Bot-Erkennung** (siehe 5.5): keine Proxy-Rotation, keine Captcha-Löser,
  kein Headless-Browser.

## 10. Änderungshistorie

- **2026-09-05** — Entwurf v1: Zielbild, Ist-Zustand, Varianten, offene Punkte.
- **2026-09-05** — Entwurf v2: Machbarkeit geklärt (Client-Direktabfrage scheidet aus;
  Auktionsgebote sind über `currentBidPrice` abrufbar; 5.000 Calls/Tag als harte Grenze),
  Sicherheitsanforderungen ausformuliert, Phasenplan ergänzt.
- **2026-09-05** — Entwurf v3: Variante E (Server-seitiges Scraping, flüchtige Anzeige) ergänzt
  und zur Empfehlung gemacht — hinter einem austauschbaren Port, mit der API-Variante als
  benanntem Ausweg.
  Sicherheitsabschnitt nach Varianten getrennt; Blockierungsrisiko, Circuit Breaker und die
  bewusste AGB-Entscheidung aufgenommen; Persistenz entfällt (kein Liquibase, keine Tabelle);
  Phase 0 um den entscheidenden Spike vom Zielhost erweitert.
