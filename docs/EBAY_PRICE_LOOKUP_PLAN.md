# Plan: eBay-Preisabfrage pro Titel im Dashboard

Ziel dieses Dokuments ist ein Umsetzungsplan für die eBay-Anbindung von w2s.
Es ist bewusst so geschrieben, dass eine **andere Claude-Code-Session ohne Vorwissen aus dem
Ursprungsgespräch** direkt damit arbeiten kann: jeder Schritt nennt die konkrete Datei, die
betroffenen Klassen/Komponenten und was sich ändert.

**Status: Entwurf v3.7, in Arbeit.**
Machbarkeit und Sicherheitslage sind geklärt (Abschnitte 3–5).
Der POC-Lauf aus Abschnitt 7.1 hat Variante E (Scraping) **widerlegt**;
der Auftraggeber verfolgt seither **Variante C (Browse API) primär** (Abschnitt 4.2),
ein Developer-Account ist beantragt mit Rückmeldung bis zum **2026-09-08**.
Offen sind die Quota-Details (Abschnitt 11) und die Detailentscheidungen aus Abschnitt 6.
Phase 0 in seiner bisherigen Form (Scraping-Spike) ist damit gegenstandslos — an seine Stelle
tritt die Verifikation gegen die eBay-Sandbox, sobald der Account da ist.

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

**Variante C (Browse API) als Standardquelle, hinter einem Port.**

Diese Empfehlung ist eine **Korrektur**: bis v3.2 stand hier Variante E (Scraping) als Standard
mit C als Ausweg.
Der POC-Lauf aus Abschnitt 7.1 hat diese Reihenfolge umgedreht — E wurde nach rund 40 Requests
dauerhaft blockiert, und zwar von einer IP, die günstiger bewertet sein dürfte als die spätere
Produktions-IP.
Der Auftraggeber hat daraufhin entschieden, **Variante C primär zu verfolgen**;
ein eBay-Developer-Account ist beantragt, mit Rückmeldung bis zum **2026-09-08**.

Der entscheidende Punkt ist, dass diese Wahl **nicht endgültig sein muss**.
Die Architektur des Projekts macht sie umkehrbar, und es gibt dafür bereits einen Präzedenzfall:
Poster sind über `PosterSource` austauschbar (IMDb per Default, TMDB hinter `tmdb.enabled`).
Dasselbe hier:

- `PurchaseOfferSource` ist der Port (Abschnitt 7, Phase 1).
- `EbayBrowseApiSource` ist der Adapter für Variante C — der Standard.
  Er braucht Zugangsdaten (5.4) und ein Quota-Budget (Abschnitt 11).
- `EbayScrapeSource` (Variante E) bleibt als benannter Rückfallweg dokumentiert, wird aber
  **nicht** gebaut, solange C verfügbar ist.
  Die Analyse in 4.1 und die Anforderungen in 5.5 bleiben für diesen Fall gültig.

Anwendungsschicht, API-Endpunkt, DTO und das gesamte Frontend sind von der Wahl **nicht** betroffen.
Der Preis dieser Korrektur ist entsprechend gering: sie kostet eine Adapter-Klasse, nicht das
Feature — genau das war der Zweck des Ports.

**Was C gegenüber E einhandelt:** einen Freigabeprozess und eine harte Tagesquote
(5.000 Calls, Abschnitt 3) anstelle eines Blockierungsrisikos.
Die Quote ist planbar, die Blockade war es nicht.
Die Aufteilung dieser Quote unter den Nutzern regelt Abschnitt 11.
**Was entfällt:** die AGB-Frage aus 5.7 — C ist der von eBay vorgesehene Weg.

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

### 5.6 Datenhaltung: keine Persistenz, Browser-Cache statt Server-Cache
- **Keine DB-Persistenz.** Entscheidend sind zwei Eigenschaften der Daten selbst:
  ein Gebot kann sich minütlich ändern, und ein Sofortkaufangebot mit Stückzahl 1 ist nach dem
  Verkauf **ganz weg**, nicht nur veraltet.
  Ein persistierter Preis wäre also nicht „etwas älter", sondern mit hoher Wahrscheinlichkeit
  schlicht falsch — und sähe dabei genauso verbindlich aus wie ein frischer.
  Kein Liquibase-Changelog, keine Tabelle, keine Entity, keine Löschfristen — **für Preisdaten**.
  Die einzige Ausnahme betrifft nicht Preise, sondern den Quota-Zustand der Browse API:
  dass das Tagesbudget erschöpft ist, muss einen Neustart überleben und wird deshalb persistiert
  (siehe 11.3.1).
- **Der Browser-Cache reicht für den Nutzer** — aber nur für ihn.
  `Cache-Control: private, max-age=…` (Vorschlag: 60–120 s) auf der Antwort von
  `GET /api/titles/{imdbId}/offers` fängt Re-Renders, Weg-und-zurück-Navigation und Doppelklicks
  desselben Benutzers ab, ohne dass der Server irgendeinen Zustand hält.
  Angulars `HttpClient` nutzt den HTTP-Cache des Browsers dafür ohne Zutun.
- **Was der Browser-Cache nicht abdeckt:** zwei Benutzer beim selben Titel, derselbe Benutzer in
  zwei Tabs, und ein Skript mit gültigem Session-Cookie.
  Dagegen hilft nur serverseitige Begrenzung.
- **Minimum auf dem Server ist deshalb nicht ein TTL-Cache, sondern In-Flight-Deduplizierung:**
  läuft für einen Titel bereits ein Abruf, hängen sich parallele Anfragen an dessen Ergebnis,
  statt einen zweiten Request nach draußen zu schicken.
  Das sind wenige Zeilen, kostet keinen Zustand über die Requestdauer hinaus und deckt genau die
  Fälle ab, die der Browser-Cache offenlässt.
- Ein kurzlebiger **In-Memory-TTL-Cache** (Minutenbereich) bleibt die Option für den Fall, dass
  der Spike Blockierungsdruck zeigt — er ist bewusst **nicht** Teil der ersten Umsetzung.
  Reihenfolge: erst In-Flight-Dedup + Cache-Header, TTL-Cache nur bei Bedarf nachrüsten.
- Der angezeigte Preis bekommt einen **„Stand: …"-Zeitstempel**, bei Auktionen sekundengenau.
  Ein erneuter Klick auf den Knopf muss den Browser-Cache umgehen können
  (Cache-Buster oder `no-cache`), sonst kann der Benutzer nicht aktualisieren.

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

1. ~~**Variante** (Abschnitt 4)~~ — **entschieden:** Variante C (Browse API) primär,
   E nur noch als dokumentierter Rückfallweg (siehe 4.2).
2. **Suchbegriff:** deutscher Titel (nur verfügbar, wenn der Metadaten-Cache ihn hat) oder
   Originalname; mit oder ohne Jahr; Kategoriefilter auf „DVDs & Blu-ray Discs" (`_sacat`).
   Die Trefferqualität ist das eigentliche Produktrisiko dieses Features:
   „Heat" findet ohne Kategoriefilter vor allem Heizungszubehör.
3. ~~**Preisdefinition**~~ — **entschieden: beides anzeigen**, Artikelpreis und Versandkosten.
   Der Nutzer soll sehen, was er tatsächlich zahlt, ohne dass zwei Angebote mit 2 € und 8 €
   Versand als gleich günstig erscheinen.
   **Vermutlich kostenlos:** die Browse API liefert `shippingOptions` innerhalb derselben
   `ItemSummary`, also ohne zusätzlichen Call — dokumentiert, aber unverifiziert (Abschnitt 8).
   **Rückfallweg, falls es doch Calls kostet:** Versandkosten nicht abrufen und den Preis als
   „ab 2,99 €" darstellen, was die Unschärfe sichtbar macht, statt sie zu verschweigen.
4. ~~**Marktplatz**~~ — **entschieden: Auswahl pro Benutzer in dessen Einstellungen.**
   Ein Dropdown, keine Ableitung aus der Sprache — die Sprache eines Nutzers sagt nichts darüber,
   wohin geliefert werden soll.
   Zulässig sind die Marktplätze zu den drei unterstützten Währungen:
   `ebay.de` (EUR), `ebay.com` (USD), `ebay.co.uk` (GBP).
   Das ist eine **neue Benutzereinstellung** und damit eine Änderung an `accountaccess`,
   nicht nur an `purchaseoffers` — siehe Phase 1b.
5. ~~**Scope**~~ — **entschieden: nur Dashboard.**
   `TitleCell`/`TitleTile` bekommen den Preisblock nur, wenn das Dashboard ihn anfordert;
   die Provider-Seiten bleiben unverändert.
   Begrenzt zugleich die Gelegenheiten, Tagesbudget zu verbrauchen.
6. ~~**Anzeigeort**~~ — **entschieden: ein Preis inline, beide im Tooltip.**
   In der Zeile bzw. auf der Kachel steht der günstigere der beiden Preise als „ab 4,50 €";
   Sofortkauf, Gebot und „Stand"-Zeitstempel stehen im Tooltip.
   Zwei Dinge folgen daraus zwingend:
   - **Der Inline-Preis muss für sich stehen.** Auf Touch-Geräten gibt es kein Hover;
     wer den Tooltip nie öffnet, sieht nur diese eine Zahl.
     Deshalb „ab" — es sagt, dass es noch etwas zu sehen gibt.
   - **Der Tooltip braucht ein Tap-Verhalten**, nicht nur Hover, und muss per Tastatur
     erreichbar sein.
     Reines `title=`-Attribut reicht dafür nicht.
7. ~~**Bounded Context**~~ — **entschieden und umgesetzt:** eigener Kontext `purchaseoffers`,
   mit Isolationsregel im `ArchitectureTest`.
8. **EPN-Affiliate-Parameter** an ausgehenden Links — **wieder offen.**
   Unter Variante E war die Frage gegenstandslos, weil es kein eBay-Konto gab, dem sich ein
   Affiliate-Bezug zuordnen ließe.
   Mit dem Developer-Account aus Variante C existiert dieser Bezug, also ist zu entscheiden, ob
   die Angebots-Links einen EPN-Parameter tragen.
   Zu bedenken: das macht aus einer Preisanzeige eine Monetarisierung, was für ein privates,
   nicht-kommerzielles Projekt eine andere Zusage gegenüber den eigenen Nutzern ist —
   und es berührt die Frage, ob die Nutzung damit noch als nicht-kommerziell gilt.
   Vorschlag: nein, mindestens bis das Feature läuft.

## 7. Phasenplan

```
Phase 0 (POC vom Zielhost + ADR)     ──► Voraussetzung; kann das Vorhaben stoppen
Phase 1 (Backend: Port + Scrape-Adapter) ──► Voraussetzung für Phase 2
Phase 2 (Frontend: Knopf + Anzeige)  ◄── braucht Phase 1
Phase 3 (CSP)                        ──► unabhängig, eigener Commit
```

### Phase 0 — POC (Wegwerf-Code, eigener Commit, klar als POC markiert)

Der POC beantwortet **drei Fragen** und baut sonst nichts.
Kein Endpunkt, kein Frontend, kein Bounded Context, kein Cache, keine Konfiguration.
Fällt eine der drei Antworten negativ aus, ändert sich der Plan — deshalb steht er vor Phase 1.

**Frage 1: Antwortet eBay unserem Server überhaupt?**
Abruf der Suchergebnisseite mit dem projekteigenen `OutboundHttpClients.USER_AGENT`,
**von der Zielmaschine aus** — nicht vom Entwicklungsrechner.
Erwartet wird echtes Ergebnis-Markup; möglich sind Captcha-Seite, `403`, oder eine leere Antwort
wie sie IMDb an Rechenzentrums-IPs liefert (im README dokumentiert).
Diese Frage ist in Minuten beantwortet und entscheidet über die gesamte Variante.

**Frage 2: Sind beide Preise zuverlässig aus dem Markup lesbar?**
Aus der abgerufenen Seite die Selektoren verifizieren und die URL-Parameter bestätigen:
`_nkw`, `_sacat` (Kategorie DVDs/Blu-ray auf ebay.de), `LH_BIN=1`, `LH_Auction=1`, `_sop`.
Insbesondere: lassen sich Sofortkaufpreis und aktuelles Gebot im Markup **unterscheiden**,
und reicht eine gemischte, preisaufsteigend sortierte Seite, oder braucht es zwei Abrufe?
Die Seite wird als **Test-Fixture eingecheckt**, damit das Parsing ab hier netzwerkfrei
entwickelt und getestet werden kann.

**Frage 3: Stimmt die Trefferqualität?**
Für 5–10 echte Watchlist-Titel (bewusst inklusive Problemfällen wie „Heat", „Up", „It")
Suchbegriff bilden, abrufen, parsen und tabellarisch ausgeben:
Titel | günstigster Sofortkauf | günstigstes Gebot | ist das plausibel der richtige Film?
Das ist das eigentliche Produktrisiko — es lässt sich durch keine Architektur beheben,
nur durch einen besseren Suchbegriff (Kategoriefilter, Jahr, deutscher Titel).

**Form:** eine kleine, klar als POC benannte Klasse oder ein `@Tag`-annotierter Test im
Backend — nicht in die Bounded-Context-Struktur einsortiert, nicht produktionsreif,
ausdrücklich zum Wegwerfen.
Ergebnis ist eine Notiz in diesem Dokument, nicht Code, der bleibt.

**Danach, vor Phase 1:** ADR-0017 schreiben (via `adr`-Skill) und die Entscheidungen aus
Abschnitt 6 treffen.

### 7.1 Ergebnis des ersten POC-Laufs (2026-09-05)

Durchgeführt aus dem Agent-Container heraus, nachdem die Egress-Routen für `ebay.de`/`ebay.com`
und `werstreamt.es` freigeschaltet wurden.
**Wichtige Einschränkung vorweg:** das ist die Egress-IP des Containers, **nicht** die des
Zielhosts. Frage 1 ist damit für diese Umgebung beantwortet, für die Produktion nicht.

**Umfeld (gesichert):**

- Der Egress-Proxy tunnelt CONNECT sauber durch und bricht TLS **nicht** auf —
  im Handshake erscheint das echte eBay-Zertifikat (Sectigo, `CN=www.ebay.co.uk`).
  Der TLS-Fingerprint gegenüber Akamai ist also unser eigener, nicht der des Proxys.
- `werstreamt.es` ist von hier aus erreichbar (HTTP 200) — der bestehende Scraper wäre bedienbar.
- jsoup 1.17.2 ist bereits Projektabhängigkeit (`pom.xml`).

**Frage 1 — Antwortet eBay unserem Server? Ja, aber nur kurz.**

| Konfiguration | Ergebnis |
| --- | --- |
| curl-Default-User-Agent | 403 (AkamaiGHost) |
| `OutboundHttpClients.USER_AGENT` allein | **403** |
| vollständiger Browser-Headersatz | 200, ~190 KB, dreimal reproduzierbar |
| derselbe Headersatz, ~10 Minuten später | 307 → `/splashui/challenge` |

Der Sprung auf 200 kam erst mit dem kompletten Satz aus `Accept`, `Accept-Language`,
`Accept-Encoding`, `sec-ch-ua`/`-mobile`/`-platform`, den vier `Sec-Fetch-*`-Headern,
`Upgrade-Insecure-Requests` und `Connection`.
Eine Leave-one-out-Analyse zur Bestimmung der Pflicht-Header war **nicht aussagekräftig**:
die Antworten sprangen unsystematisch zwischen 200, 307 und 403.
Das spricht für ein Reputations-Scoring von Akamai, nicht für eine feste Header-Liste.

**Nach rund 40 Requests in etwa 25 Minuten war die IP marktplatzübergreifend gesperrt.**
Fünf Abrufe im Minutenabstand — also bereits mit dem in 5.2 vorgesehenen Rate-Limit — lieferten
ausnahmslos 307 auf `/splashui/challenge` („Bitte entschuldigen Sie die Störung…").
Der Zugang hat sich innerhalb der Beobachtungszeit **nicht von selbst erholt**;
zuletzt antwortete auch `www.ebay.com` mit 403.

**Frage 2 — Sind beide Preise aus dem Markup lesbar? Unbeantwortet.**
Die 200er-Antworten waren gzip-komprimiert und wurden im Moment des Erfolgs nicht dekomprimiert
weggeschrieben; als das nachgeholt werden sollte, war der Zugang bereits heruntergestuft.
Es liegt **kein Ergebnismarkup** vor.
Selektoren, die Unterscheidbarkeit von Sofortkauf und laufendem Gebot und die URL-Parameter
(`_sacat`, `LH_BIN`, `LH_Auction`, `_sop`) sind damit **weiterhin unverifiziert**,
und es ist **kein Fixture eingecheckt**.

**Frage 3 — Trefferqualität? Unbeantwortet**, weil sie Frage 2 voraussetzt.

**Was das für die Empfehlung bedeutet.**
Der POC hat die in 4.1 als „ernster als die AGB-Frage" benannte Sorge nicht ausgeräumt, sondern
bestätigt: Variante E funktioniert kurz und kippt dann.
Ein Feature, das an eine bewusste Nutzeraktion gebunden ist (Abschnitt 9), erzeugt zwar deutlich
weniger Last als dieser Testlauf — aber die Sperre trat bereits bei einem Volumen ein, das eine
Handvoll aktiver Nutzer an einem Abend erreichen kann.
Zwei Punkte sind vor einer Entscheidung zu klären:

1. **Der Headersatz ist eine offene Frage an Abschnitt 5.5.**
   Der projekteigene `USER_AGENT` allein reicht nachweislich nicht (403).
   Ein vollständiger, in sich konsistenter Browser-Headersatz ist wohl noch dasselbe Vorgeben
   eines Browsers, nur vollständig statt halb — aber er geht über das hinaus, was das Projekt
   heute tut, und ist deshalb eine bewusste Entscheidung für das ADR, kein Implementierungsdetail.
2. **Ob die Produktions-IP sich anders verhält**, ist offen.
   Eine Rechenzentrums-IP wird eher schlechter bewertet als besser.

Der in 4.2 benannte Ausweg — `EbayBrowseApiSource` hinter demselben Port — gewinnt durch dieses
Ergebnis an Gewicht.
Die Architekturentscheidung (Port + austauschbarer Adapter) trägt unverändert; betroffen ist nur
die Wahl des Standardadapters.

### Phase 1 — Backend
Neuer Bounded Context `purchaseoffers` unter `tech.dobler.where2stream`, Aufbau nach ADR-0014:

- `domain`: `OfferPrice` (Betrag + Währung als Value Object, ADR-0009), `TitleOffers`
  (günstigster Sofortkauf, günstigstes laufendes Gebot, Abrufzeitpunkt).
- `port/out`: `PurchaseOfferSource` — die austauschbare Schnittstelle (siehe 4.2).
- `adapter/out/ebay`:
  - `EbayProperties` (`@ConfigurationProperties("ebay")`): Basis-URLs (OAuth und Browse),
    Marktplatz, Kategorie, `rateLimit`, Quota-Einstellungen (Deckel, Reset-Zone und -Uhrzeit),
    Circuit-Breaker-Schwellen, `enabled` (Default `false`) sowie `clientId`/`clientSecret`.
    Wegen 5.4 mit **maskierendem `toString()`** — ein Record würde das Secret sonst in jeden
    Log-Eintrag schreiben.
  - `EbayOAuthTokenProvider`: Client-Credentials-Grant gegen
    `POST /identity/v1/oauth2/token`, Token nur im Speicher, Erneuerung vor Ablauf,
    niemals geloggt und niemals an den Client ausgeliefert.
  - `EbayBrowseApiSource implements PurchaseOfferSource`: `HttpClientFactory`-Seam,
    `RateLimiter`, Degradation auf „kein Ergebnis".
    **Vorbild ist `ImdbSuggestionSource`** (JSON/REST über `HttpClient`), nicht
    `WerStreamtEsSource` — das ist der jsoup-Fall und hier nicht einschlägig.
    Die Abbildung von eBay-JSON auf `TitleOffers` liegt in **statischen, package-private
    Methoden**, damit sie netzwerkfrei testbar ist und bei einer Abweichung der realen Antwort
    an genau einer Stelle korrigiert werden muss.
- `application`: `TitleOfferService` — In-Flight-Deduplizierung und Circuit Breaker.
  **Kein** TTL-Cache in der ersten Umsetzung (siehe 5.6).
- `adapter/in/api`: `PurchaseOfferApiController`, `GET /api/titles/{imdbId}/offers`,
  mit den Prüfungen aus 5.1 und `Cache-Control: private, max-age=…` (siehe 5.6).
- **Keine** Tabelle und **keine** Entity für Angebote oder Preise — die werden nicht gespeichert.
  Ein Liquibase-Changelog gibt es dennoch, aber ausschließlich für die Quota-Tabelle aus 11.3.1.
- Per-User- und globaler Tageszähler samt Reset (Abschnitt 11), inklusive der Auswertung von
  eBays Kontingent-Antwort und des Logging aus 11.3.1.
- `port/in`: Zugriff auf die Benutzerzahl für `n` über einen `port.in` des
  `accountaccess`-Kontexts, nicht per Direktzugriff (ADR-0014, ADR-0017).
- `ArchitectureTest` (ArchUnit) um die Isolationsregel für den neuen Kontext erweitern.

**Vorbereitend, als eigener Pfadfinder-Commit:**
`HttpClientFactory` und `OutboundHttpClients` liegen heute in
`titlecatalog/adapter/out` — ein Kontext, den `purchaseoffers` nach ADR-0014 nicht importieren
darf.
Beide gehören nach `shared/platform/outbound`, wo `RateLimiter` bereits liegt.
Der Umzug ist rein mechanisch (Paketwechsel plus Importe in den vier bestehenden Integrationen)
und gehört getrennt vom Feature committet.

**Tests — netzwerkfrei, für jeden Teil:**

- `EbayBrowseApiSource`: `@Mock HttpClient` + `@Mock HttpResponse<String>`, JSON als Text-Literal,
  Seam als `() -> httpClient`.
  Muster ist `ImdbSuggestionSourceTest` — inklusive der dortigen Fehlerpfade
  (Nicht-200, `IOException`, `InterruptedException` mit Wiederherstellen des Interrupt-Flags).
- `EbayOAuthTokenProvider`: Token-Erneuerung, Wiederverwendung eines gültigen Tokens,
  Verhalten bei abgelehnten Zugangsdaten — und ein Test, der belegt, dass das Secret
  **nicht** im `toString()` der Properties auftaucht.
- Quota: Per-User- und globaler Zähler, Vorrang von eBays Kontingent-Antwort, Reset über einen
  **fixen Clock** gegen **beide** Zeitzonenzustände (PST und PDT, siehe ADR-0017).
- `TitleOfferService`: In-Flight-Deduplizierung, Circuit Breaker, Degradation.
- `PurchaseOfferApiController`: MockMvc, inklusive „ID nicht auf meiner Watchlist → 404",
  gesetztem `Cache-Control` und der Host-Allowlist aus 5.3.
- AssertJ + Mockito (ADR-0005), Mehrfeld-Prüfungen als **eine** `extracting(...)`-Assertion
  (Skill `consolidate-test-assertions`; bei mehreren Extractors gehört `.isNotNull()` in
  dieselbe Kette).

**Was ohne den Developer-Account nicht geht** — und was das für Phase 1 bedeutet:
Die reale Antwortstruktur der Browse API ist dokumentiert, aber unverifiziert;
dasselbe gilt für den Fehlercode bei Kontingenterschöpfung und die Kontingent-Header.
Phase 1 wird deshalb **gegen die dokumentierte Struktur** gebaut und vollständig mit Mocks
getestet.
Es gibt in Phase 1 **keinen** Test, der echte eBay-Endpunkte aufruft.
Weicht die reale Antwort ab, ist die Korrektur auf die statischen Mapping-Methoden und deren
Tests begrenzt — das ist der Grund, sie zu isolieren.

### Phase 1b — Marktplatz als Benutzereinstellung (`accountaccess`)

Folgt aus Entscheidung 6.4 und ist der einzige Teil des Features, der **außerhalb** von
`purchaseoffers` liegt.
Das Projekt hat für Benutzereinstellungen ein etabliertes Muster — `Language`, `Theme`,
`ViewMode`, `TilesPerRow` und die beiden Anzeige-Flags sind alle so gebaut —, dem hier zu folgen
ist:

- `Marketplace`-Enum in `accountaccess/domain` (`EBAY_DE`, `EBAY_COM`, `EBAY_CO_UK`),
  jeweils mit Marktplatz-ID und Währung.
- Feld in `UserPreferences`, Liquibase-Changelog für die neue Spalte, Default `EBAY_DE`.
- `MarketplaceUpdateCommand`/`MarketplaceUpdateRequest` plus Endpunkt an `MeApiController`,
  analog zu `LanguageUpdateRequest`.
- Auslieferung über `MeDto`, damit der Client sie kennt.
- **Zugriff aus `purchaseoffers` ausschließlich über einen `port.in`** von `accountaccess`
  (ADR-0014) — derselbe Weg, über den auch die Benutzerzahl `n` für die Quota kommt.
- Frontend: Dropdown in den Einstellungen, i18n in `de.json` **und** `en.json`.

Konsequenz, die bewusst hingenommen wird: zwei Nutzer sehen für denselben Titel verschiedene
Preise, weil sie verschiedene Marktplätze abfragen.
Das ist unproblematisch, weil nichts zwischengespeichert wird (5.6) — es gäbe ohnehin keinen
geteilten Wert, der falsch sein könnte.

**Architektonisch zu prüfen, bevor Phase 1b umgesetzt wird:**
Die Marktplatz-Einstellung schafft eine neue Abhängigkeit `purchaseoffers → accountaccess`
(zusätzlich zu der für die Benutzerzahl `n`).
Zu klären ist, ob das regelkonform bleibt und insbesondere **keinen Zyklus** zwischen den
Kontexten erzeugt:

- Wandert der `Marketplace`-Typ über den `port.in` nach draußen, wird er Teil des
  veröffentlichten Vertrags von `accountaccess` — wie `ImdbEntry`/`WatchlistDate` es für
  `watchlist` sind, die dafür in `ArchitectureTest` eigens ausgenommen werden mussten.
  Entweder braucht `Marketplace` dieselbe Ausnahme, oder er gehört in den Shared Kernel,
  oder der Port gibt einen neutraleren Typ heraus.
- Ein Zyklus entstünde, sobald `accountaccess` seinerseits etwas aus `purchaseoffers` bräuchte —
  etwa um beim Löschen eines Benutzers dessen Quota-Zeilen mitzunehmen (ADR-0017 nennt das als
  Bedenken).
  Genau diese beiden Anforderungen zeigen in entgegengesetzte Richtungen und sind der
  wahrscheinlichste Weg, wie hier doch ein Zyklus entsteht.
- Die bestehenden Isolationsregeln prüfen jeweils **eine** Richtung und bemerken einen Zyklus
  daher nicht.
  Eine ArchUnit-Regel auf Zyklusfreiheit der Kontext-Slices
  (`slices().matching(...).should().beFreeOfCycles()`) wäre die Ergänzung, die das abdeckt.

**Gemessen am 2026-09-05, mit einer verworfenen Probe-Regel:**
Der Code ist **heute schon nicht zyklenfrei** — unabhängig von diesem Feature.

- Zyklen über `shared` sind erwartbar und dokumentiert:
  `ApiExceptionHandler` kennt bewusst die Exception-Typen aller Kontexte.
  Rechnet man `shared` heraus, bleibt trotzdem einer übrig.
- **`accountaccess` → `titlecatalog` → `accountaccess`.**
  `MeApiController` hängt an `titlecatalog.port.in.PosterAttributionPort`
  (für den TMDB-Attributionshinweis), und `titlecatalog` hängt über `CurrentUserPort` zurück
  an `accountaccess`.
  Beide Richtungen laufen über *veröffentlichte* Ports — die bestehenden Regeln sind deshalb zu
  Recht grün, der Kreis existiert dennoch.
- **Erledigt am 2026-09-05:** Der Zyklus ist aufgelöst.
  `PosterAttributionPort` liegt jetzt in `shared/platform/api` statt in `titlecatalog/port/in`.
  Die Begründung ist nicht Bequemlichkeit: ein Flag „die Oberfläche muss diesen Hinweis zeigen" ist
  weder eine Tatsache von `accountaccess` noch eine von `titlecatalog`, sondern ein Vertrag
  zwischen beiden — und `shared` ist aus demselben Grund ohnehin von den Isolationsregeln
  ausgenommen.
  Dazu kam die ArchUnit-Regel `bounded_contexts_are_free_of_cycles`, die `shared` in beiden
  Richtungen ausklammert (sonst wäre sie wegen `ApiExceptionHandler` dauerhaft rot und damit
  wertlos). Verifiziert: mit einer temporär wiedereingeführten Kante schlägt sie fehl.
- Für das Feature selbst gilt: `purchaseoffers → accountaccess` erzeugt für sich genommen
  **keinen** Zyklus, weil `accountaccess` nichts aus `purchaseoffers` braucht.
  Das kippt erst, wenn die in ADR-0017 erwähnte Löschweitergabe (Benutzer löschen → Quota-Zeilen
  mitnehmen) als Abhängigkeit von `accountaccess` auf `purchaseoffers` gebaut wird.
  Sie gehört deshalb andersherum gelöst — als Ereignis oder als Aufräumlauf in
  `purchaseoffers` —, nicht als Direktaufruf.

### Phase 2 — Frontend
- `core/api/offers-api.ts`: `OffersApi.get(imdbId)` — dünn, wie die übrigen `*-api.ts`.
- `core/offers-store.ts`: Zustand je `imdbId` (`idle | loading | loaded | error`),
  Deduplizierung, **kein** automatischer Abruf beim Seitenaufbau. Muster: `SeenStore`.
- `shared/offer-prices/offer-prices.ts`: dumme Komponente — Knopf im Ruhezustand,
  danach **ein** Preis inline („ab 4,50 €") und beide Preise samt „Stand"-Zeitstempel im Tooltip
  (Entscheidung 6.6), plus die Zustände „keine Angebote", „derzeit nicht verfügbar",
  „dein Tagesbudget erschöpft" und „gemeinsames Tagesbudget erschöpft" (ADR-0017).
  Ladezustand als **Skeleton-Bar** (`.skeleton-bar--narrow`), nicht als Spinner
  (Projektkonvention, siehe `CLAUDE.md`).
  Der Tooltip muss per Tap **und** per Tastatur zu öffnen sein, nicht nur per Hover —
  ein `title=`-Attribut genügt nicht.
- Einbau in `CatalogTable` und `TitleGrid`/`TitleTile`, **nur vom Dashboard aus aktiviert**
  (Entscheidung 6.5); die Provider-Seiten rendern dieselben Komponenten ohne Preisblock.
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

- **Ob eBay Requests vom Zielhost überhaupt beantwortet.**
  Der wichtigste offene Punkt des gesamten Plans.
  Aus dem Agent-Container heraus: kurzzeitig ja, dann dauerhaft Challenge (Abschnitt 7.1).
  Vom Zielhost aus ungeprüft.
- **Ob und wann eine einmal ausgelöste Sperre wieder abläuft** — im POC innerhalb von
  fünf Minuten nicht.
- Die aktuellen **CSS-Selektoren** der eBay-Suchergebnisseite und ob sich Sofortkauf- und
  Auktionsangebote dort zuverlässig unterscheiden lassen.
- Die URL-Parameter `_sacat` (Kategorie DVDs/Blu-ray auf `ebay.de`), `LH_BIN`, `LH_Auction`,
  `_sop` — plausibel, aber unverifiziert.
- Ob ein gemischter Abruf (eine Seite, preisaufsteigend) beide Preise liefert oder zwei getrennte
  Abrufe nötig sind.
Für die nun primäre Variante C — gegen die Sandbox zu prüfen, sobald der Account da ist:

- Kombinierbarkeit von `sort=price` mit `buyingOptions:{AUCTION}`.
- Ob sich beide Angebotsarten in **einem** Call abfragen lassen
  (etwa `buyingOptions:{FIXED_PRICE|AUCTION}`) und ob eine einzelne preisaufsteigend sortierte
  Ergebnisseite dann wirklich beide Bestwerte enthält.
  Das würde das Titel-Budget aus Abschnitt 11 verdoppeln.
- **Wann eBays Tageskontingent zurückgesetzt wird.**
  Arbeitshypothese ist 12 Uhr Pazifik-Zeit, belegt nur durch eine Gemini-Auskunft,
  dazu doppeldeutig (Mittag/Mitternacht) — siehe 11.5.
- **Der genaue Fehlercode bei Kontingenterschöpfung** (vermutlich `2001`/`RATE_LIMIT`) und
  **welche Kontingent-Header** die Browse API mitschickt.
  Beides entscheidet, wie zuverlässig 11.3.1 greift.
- Ob die Sandbox dieselben Kontingentgrenzen hat wie die Produktionsumgebung.
- **Ob `shippingOptions` in der `ItemSummary` der Suchantwort enthalten ist** — also ob die
  Versandkosten aus Entscheidung 6.3 ohne zusätzlichen Call zu haben sind.
  Die Dokumentation legt das nahe; bestätigt ist es nicht.
  Fällt die Antwort negativ aus, greift der dort benannte Rückfallweg („ab 2,99 €").
- Ob sich die drei Marktplätze aus Entscheidung 6.4 über den `X-EBAY-C-MARKETPLACE-ID`-Header
  eines einzigen Application-Tokens abfragen lassen, oder ob je Marktplatz eigene Zugangsdaten
  nötig sind.

## 9. Nicht-Ziele

- Kein Kaufabschluss, keine Gebotsabgabe, keine eBay-Anmeldung des Benutzers aus w2s heraus.
- Keine Preishistorie, keine Preisalarme in dieser Ausbaustufe.
- Keine Verkäufer-, Versand- oder Zustandsbewertung — nur die zwei geforderten Preise.
- **Kein Massen-Abruf** („Preise für alle Titel laden") und kein Vorladen im Hintergrund.
  Der Abruf bleibt an eine bewusste Nutzeraktion gebunden.
  Die Regel stammt aus der Zeit von Variante E, wo jeder Request das Blockierungsrisiko erhöhte;
  unter Variante C ist die Begründung eine andere, aber nicht schwächer:
  jeder Abruf kostet zwei Calls aus einem Tagesbudget, das sich **alle** Nutzer teilen
  (Abschnitt 11).
  Ein Knopf „alle Preise laden" auf einer 200-Titel-Watchlist verbraucht 400 Calls — 8 % des
  Tagesbudgets für einen einzigen Klick.
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
- **2026-09-05** — Entwurf v3.1: Datenhaltung vereinfacht — keine DB-Persistenz und kein
  Server-TTL-Cache in der ersten Umsetzung, stattdessen `Cache-Control: private` für den
  Browser-Cache plus In-Flight-Deduplizierung auf dem Server (Abschnitt 5.6);
  Phase 0 als POC mit drei Fragen ausformuliert.
- **2026-09-05** — Entwurf v3.2: Ergebnis des ersten POC-Laufs als Abschnitt 7.1 aufgenommen.
  Frage 1 aus dem Agent-Container heraus beantwortet (kurzzeitig 200, danach dauerhafte
  Akamai-Challenge), Fragen 2 und 3 bleiben offen — es liegt kein Ergebnismarkup und kein Fixture
  vor.
  Neuer offener Punkt: der projekteigene `USER_AGENT` allein genügt nicht, ein vollständiger
  Browser-Headersatz ist nötig und damit eine Entscheidung an Abschnitt 5.5.
- **2026-09-05** — Entwurf v3.3: **Variantenentscheidung umgedreht.**
  Als Konsequenz aus 7.1 wird Variante C (Browse API) primär verfolgt, Variante E nur noch als
  dokumentierter Rückfallweg geführt (Abschnitt 4.2, offene Entscheidung 6.1 damit erledigt).
  Neuer Abschnitt 11 zur Aufteilung der 5.000-Calls-Tagesquote unter den Nutzern nach der Formel
  des Auftraggebers, inklusive der daraus folgenden Notwendigkeit eines globalen Tageslimits.
  Abschnitt 8 auf die für C zu prüfenden Punkte umgestellt.
- **2026-09-05** — Entwurf v3.4: Quota-Entscheidungen des Auftraggebers eingearbeitet.
  Globales Tageslimit auf 5.000 ohne Sicherheitsabschlag; der `n`-Vorschlag aus 11.4 ist
  angenommen.
  Neuer Abschnitt 11.3.1: eBays Kontingent-Antwort schlägt den eigenen Zähler, und der
  Erschöpfungszustand wird für den Tag in der Datenbank festgeschrieben, damit ein Deploy die
  Sperre nicht aufhebt — als benannte Ausnahme von 5.6, die weiterhin nur Preisdaten meint.
  Reset-Hypothese von „UTC-Mitternacht" auf „12 Uhr Pazifik-Zeit" geändert (Quelle: Gemini,
  unbelegt); der Zeitpunkt wird konfigurierbar geführt, weil „12 Uhr" doppeldeutig ist, und die
  Zone als `America/Los_Angeles` statt als fester PST-Offset, weil Pazifik-Zeit Sommerzeit hat.
  Zwei Wege zur Überprüfung der Hypothese beschrieben (passiv aus dem Log, aktiv per
  Ausschöpfung nach vollständiger Implementierung).
- **2026-09-05** — Entwurf v3.5: [ADR-0017](adr/0017-quota-verwaltung-fuer-die-ebay-browse-api.md)
  angelegt (Status `Proposed`), das die Quota-Entscheidungen aus Abschnitt 11 festhält —
  darunter die Aufteilung auf zwei eigene Tabellen statt Spalten an `AppUser`.
  Die reale Nutzerzahl (fünf) in 11.1 ergänzt, mitsamt der Feststellung, dass das Per-User-Limit
  bei diesem `n` keine Fairness-Bremse mehr ist, sondern nur noch Missbrauchsschutz —
  vom Auftraggeber akzeptiert.
  Aufbewahrung der Per-User-Zeilen bewusst ungeregelt gelassen (Ausbaustufe).
- **2026-09-05** — Entwurf v3.6: Phase 1 auf Variante C umgeschrieben.
  Die Liste beschrieb noch `EbayScrapeSource` mit jsoup und dem Fixture aus Phase 0 — ein
  Überbleibsel aus v3.2, das die Variantenentscheidung aus v3.3 nicht mitbekommen hatte.
  Stattdessen jetzt `EbayOAuthTokenProvider` und `EbayBrowseApiSource` nach dem Vorbild von
  `ImdbSuggestionSource`, Testliste je Baustein, und der vorbereitende Pfadfinder-Umzug von
  `HttpClientFactory`/`OutboundHttpClients` nach `shared/platform/outbound`.
  Ausdrücklich festgehalten, was ohne den Developer-Account nicht geht.

- **2026-09-05** — Entwurf v3.7: die vier offenen UX-Entscheidungen getroffen (6.3 bis 6.6).
  Ein Preis inline plus beide im Tooltip, nur auf dem Dashboard, Artikelpreis **und** Versand,
  Marktplatz als Auswahl pro Benutzer.
  Letzteres zieht eine neue Benutzereinstellung nach sich und damit eine Änderung an
  `accountaccess` — als **Phase 1b** aufgenommen, weil es der einzige Teil des Features außerhalb
  von `purchaseoffers` ist.
  Zwei Folgerungen aus dem Tooltip festgehalten: der Inline-Preis muss ohne ihn auskommen
  (Touch kennt kein Hover), und der Tooltip braucht Tap- und Tastaturbedienung.
  Entscheidung 7 (eigener Bounded Context) als umgesetzt markiert;
  Entscheidung 8 (EPN-Affiliate) **wieder geöffnet** — unter Variante E war sie gegenstandslos,
  mit einem Developer-Account ist sie es nicht mehr.
  Begründung des Massen-Abruf-Verzichts in den Nicht-Zielen von „Blockierungsrisiko" auf
  „gemeinsames Tagesbudget" umgestellt.
  Abschnitt 8 um die Versandkosten- und die Marktplatz-Header-Frage ergänzt.

## 11. Quota-Aufteilung unter den Nutzern (Variante C)

Nachdem der POC-Lauf (Abschnitt 7.1) das Blockierungsrisiko von Variante E konkret belegt hat,
hat der Auftraggeber entschieden: **Variante C (eBay Developer Program, Browse API) wird ab sofort primär verfolgt.**
Ein Developer-Account ist beantragt; Rückmeldung wird bis zum 2026-09-08 erwartet.
Damit wird die harte Randbedingung aus Abschnitt 3 — 5.000 Calls pro Tag und Applikation, nicht pro Benutzer —
zur zentralen Betriebsfrage: das Tagesbudget muss unter allen Nutzern aufgeteilt werden.

### 11.1 Die Aufteilungsformel

Vorgabe des Auftraggebers, wörtlich:

```
requests_pro_nutzer = 5000 / (anzahl_der_nutzer / 2)
```

Algebraisch ist das `10000 / n` — das Tagesbudget wird also **bewusst doppelt überbucht**.
Die zugrundeliegende Annahme ist ausdrücklich benannt:
an einem gegebenen Tag ruft höchstens die Hälfte der registrierten Nutzer tatsächlich Preise ab.
Solange diese Annahme hält, bleibt die Summe der real verbrauchten Requests unter 5.000;
verletzen mehr als die Hälfte der Nutzer die Annahme, ist das Budget vor Tagesende erschöpft (siehe 11.3).
Das ist eine Entscheidung des Auftraggebers in Kenntnis dieses Punkts, kein Rechenfehler —
sie tauscht ungenutztes Kontingent gegen ein höheres Per-User-Limit und nimmt dafür das Erschöpfungsrisiko in Kauf.

Da eine Titelabfrage **zwei** API-Calls kostet (siehe 11.2),
ist das Budget in *Titeln* halb so groß wie in *Requests*:

| Registrierte Nutzer (n) | Requests/Nutzer/Tag (10000/n) | Titelabfragen/Nutzer/Tag |
| --- | --- | --- |
| 2 | 5.000 | 2.500 |
| 5 | 2.000 | 1.000 |
| 10 | 1.000 | 500 |
| 25 | 400 | 200 |
| 50 | 200 | 100 |

Zur Einordnung: die 200-Titel-Watchlist aus Abschnitt 3 wäre bei 25 Nutzern
genau eine vollständige „alles einmal abfragen"-Runde pro Tag —
die Bindung an eine bewusste Nutzeraktion ohne Massen-Abruf (Abschnitt 9) bleibt also auch bei Variante C wesentlich.

**Der reale Betriebsfall liegt heute am oberen Ende der Tabelle:**
die Anwendung hat derzeit **fünf** registrierte Nutzer, und die Zahl wird absehbar nicht deutlich
wachsen.
Bei `n` = 5 bekommt jeder Nutzer 2.000 Requests, also 1.000 Titelabfragen am Tag —
mehr, als eine realistische Watchlist hergibt.
Das Per-User-Limit ist in dieser Konstellation **keine Fairness-Bremse**, sondern nur noch ein
Schutz gegen Fehlbedienung und gegen ein Skript mit gültigem Session-Cookie;
den Kontingentschutz trägt allein der globale Deckel.
Der Auftraggeber hat das bewusst akzeptiert.
Die Aufteilungsformel entfaltet ihren eigentlichen Zweck erst, wenn `n` deutlich steigt —
sie ist damit eher Vorsorge als aktive Begrenzung.

### 11.2 Zwei Calls je Titel — mit einem offenen Punkt

Abschnitt 3 legt fest, dass Sofortkauf und Auktion über getrennte Filter abgefragt werden:
ein Request mit `buyingOptions:{FIXED_PRICE}`, einer mit `buyingOptions:{AUCTION}`.
Alle Zahlen in 11.1 rechnen mit diesem Faktor 2.

**Offener Punkt, der das Titel-Budget verdoppeln würde:**
ob sich beide Angebotsarten in *einem* Call kombinieren lassen
(etwa `buyingOptions:{FIXED_PRICE|AUCTION}`), ist im bisherigen Plan nicht verifiziert —
Abschnitt 8 führt bereits die verwandte Frage der Kombinierbarkeit von `sort=price` mit `buyingOptions:{AUCTION}` als ungeprüft.
Selbst wenn der kombinierte Filter funktioniert, ist damit noch nicht gezeigt,
dass eine einzelne preisaufsteigend sortierte Ergebnisseite zuverlässig **beide** Bestwerte enthält:
der günstigste Sofortkauf und das günstigste laufende Gebot müssen nicht beide auf der ersten Seite liegen.
Der Punkt gehört daher in die Verifikation nach Erhalt des Developer-Accounts (Sandbox reicht dafür)
und wird in Abschnitt 8 mitgeführt.
Bis dahin gilt konservativ der Faktor 2.

### 11.3 Überbuchung erzwingt ein globales Tageslimit

Die Formel verteilt rechnerisch 10.000 Requests, real existieren 5.000.
**Das Per-User-Limit allein schützt das Kontingent deshalb nicht:**
wenn mehr als die Hälfte der Nutzer ihr Limit ausschöpft, brauchen die ersten aktiven Nutzer das gemeinsame Budget auf,
und alle weiteren Calls laufen gegen eBays harte Quote — mit Fehlern, die wir nicht mehr kontrollieren.
Es braucht daher zwingend **zwei** Zähler:

1. **Per-User-Tageszähler**: `10000 / n` Requests, danach ist für diesen Nutzer Schluss.
2. **Globaler Tageszähler**: hartes Limit bei **5.000** (Entscheidung des Auftraggebers),
   der greift, bevor eBay selbst ablehnt.
   Ein Sicherheitsabschlag wird bewusst **nicht** eingebaut — der Deckel liegt genau auf dem
   Kontingent.
   Die Absicherung gegen Zählungenauigkeiten und Retries übernimmt stattdessen die
   Rückmeldung von eBay selbst (siehe 11.3.1):
   wer sich verzählt, merkt es an der Quota-Antwort und nicht an einem Puffer.

Verhalten bei Erschöpfung — im Sinne von 5.2/5.5 sichtbare Degradation statt Fehler:

- Kein Fehler und keine Exception im Nutzer-Request.
- Der Endpunkt antwortet mit einem regulären Ergebnis „Tagesbudget erschöpft",
  das Frontend zeigt eine verständliche Meldung
  (z. B. „Preisabfragen sind für heute ausgeschöpft, morgen wieder verfügbar"),
  analog zum Zustand „derzeit nicht verfügbar" aus Phase 2.
- Per-User-Limit erreicht: gleiche Mechanik, aber mit auf den Nutzer bezogener Meldung —
  der Unterschied ist für den Nutzer relevant (bei ihm hilft Warten bis morgen, global auch).
- Beides wird auf `info`/`warn` geloggt, damit erkennbar ist, wie oft die Annahme aus 11.1 reißt.

#### 11.3.1 eBays Quota-Antwort ist die maßgebliche Wahrheit

Unser globaler Zähler ist eine *Schätzung* des Verbrauchs, eBays Kontingentstand ist die
Tatsache.
Beide können auseinanderlaufen — durch Retries, durch parallele Requests, durch Calls, die
unterwegs scheitern und trotzdem gezählt wurden (oder umgekehrt), und durch jeden Neustart der
Anwendung.
Deshalb gilt: **meldet eBay, dass das Tageslimit erreicht ist, ist der Tag sofort beendet** —
unabhängig davon, was unser eigener Zähler gerade sagt.

Konkret:

- Trifft eine Antwort ein, die auf Kontingenterschöpfung hinweist
  (bei der Browse API der Fehler `2001`/`RATE_LIMIT` — **die genaue Kennung ist gegen die Sandbox
  zu verifizieren**, siehe Abschnitt 8),
  wird der globale Tageszähler sofort auf „erschöpft" gesetzt.
- Dieser Zustand wird **in der Datenbank für den betreffenden Tag festgeschrieben**,
  nicht nur im Speicher gehalten.
  Damit übersteht die Sperre einen Neustart der Anwendung — sonst würde ein Deploy am Abend den
  Zähler zurücksetzen und die Anwendung liefe erneut gegen ein bei eBay längst erschöpftes
  Kontingent.
- Ab diesem Punkt wird bis zum nächsten Reset (11.5) **kein** Call mehr nach draußen geschickt.
  Das Feature degradiert wie oben beschrieben.
- Die vollständige Antwort wird bei diesem Ereignis auf `warn` geloggt — Statuscode, Fehlercode,
  Fehlertext und alle Header, die eBay zum Kontingent mitschickt.
  Das ist zugleich die Datenquelle für 11.5.

**Das ist eine bewusste Ausnahme von 5.6.**
Dort steht „keine DB-Persistenz" — das gilt für **Preisdaten**, die flüchtig sind und deren
Speicherung falsche Werte konservieren würde.
Quota-Zustand ist das Gegenteil: er ist tagesstabil, klein und **muss** einen Neustart
überleben, um seinen Zweck zu erfüllen.
Es braucht dafür ein Liquibase-Changelog und eine schmale Tabelle
(Vorschlag: Quota-Tag, verbrauchte Calls, Zeitpunkt der Erschöpfung),
aber weiterhin **keine** Tabelle für Angebote oder Preise.

### 11.4 Was „anzahl_der_nutzer" konkret ist — entschieden

Die Formel ließ offen, was `n` genau ist. Drei Punkte standen zur Entscheidung:

- **Grundmenge:** alle registrierten Nutzer aus der Datenbank,
  oder nur eine Teilmenge (z. B. Nutzer mit nicht-leerer Watchlist)?
- **Ermittlungszeitpunkt:** beim Anwendungsstart, einmal täglich, oder pro Request?
  Pro Request wäre exakt, macht das Limit aber im Tagesverlauf beweglich und schwer erklärbar;
  nur beim Anwendungsstart veraltet bei langen Laufzeiten.
- **Neue Nutzer mitten am Tag:** bekommen sie sofort ein Limit (womit die Summe der verteilten
  Limits weiter steigt), oder erst ab dem Folgetag?

**Entschieden (Auftraggeber):** `n` = Anzahl registrierter Nutzer laut Datenbank,
ermittelt **einmal täglich beim Zurücksetzen der Zähler** (11.5) und für den Tag eingefroren.
Neue Nutzer mitten am Tag erhalten sofort dasselbe Tageslimit wie alle anderen,
ohne dass bestehende Limits neu berechnet werden —
das erhöht die Überbuchung geringfügig, ist aber durch das globale Limit (11.3) abgesichert
und vermeidet, dass sich ein bereits kommuniziertes Limit im Tagesverlauf ändert.

### 11.5 Zurücksetzen der Zähler

Beide Zähler (per User und global) werden einmal täglich zurückgesetzt.
Der Reset sollte mit dem Zeitpunkt zusammenfallen, zu dem eBay das Applikations-Kontingent erneuert —
sonst laufen unser Budgetfenster und eBays Fenster gegeneinander,
und ein frisch zurückgesetzter lokaler Zähler kann auf ein bei eBay noch erschöpftes Kontingent treffen.

**Arbeitshypothese: Reset um 12 Uhr Pazifik-Zeit.**
Diese Annahme stammt aus einer Auskunft von Gemini und ist **durch keine Primärquelle belegt** —
weder durch die in Abschnitt 3 verlinkten Quellen noch durch eBays Dokumentation.
Sie ersetzt die frühere, ebenso unbelegte Annahme „UTC-Mitternacht".
Der Auftraggeber hat entschieden, sie **bewusst als Hypothese mitzunehmen** und empirisch zu
prüfen, statt die Umsetzung darauf warten zu lassen.

Zwei Präzisierungen sind für die Implementierung nötig:

- **Mittag oder Mitternacht?** „12 Uhr" ist doppeldeutig.
  Der Reset-Zeitpunkt wird deshalb als **konfigurierbarer Wert** geführt
  (`ebay.quota.reset-time`, Default `00:00`), nicht als Konstante im Code.
  Ist die Lesart falsch, kostet die Korrektur einen Konfigurationswert statt eines Releases.
- **Nicht PST, sondern `America/Los_Angeles`.**
  Pazifik-Zeit wechselt zwischen PST (UTC−8) und PDT (UTC−7);
  im September gilt PDT.
  Ein fest verdrahteter Offset läuft zweimal im Jahr um eine Stunde daneben —
  genau dann, wenn ein zu früher Reset gegen ein noch erschöpftes Kontingent läuft.
  Die Zone gehört als `ZoneId` konfiguriert, die Zeitrechnung über den bestehenden `TimeService`
  (`shared/platform`), nicht über `LocalDateTime.now()`.

**Wie die Hypothese überprüft wird** — zwei Wege, in dieser Reihenfolge:

1. **Passiv, aus dem Betrieb.** Das Logging aus 11.3.1 hält bei jeder Kontingenterschöpfung den
   genauen Zeitpunkt und alle von eBay mitgeschickten Header fest.
   Kommen zwei solche Ereignisse zusammen mit dem jeweils nächsten erfolgreichen Call, lässt sich
   das Reset-Fenster eingrenzen, ohne irgendetwas zu provozieren.
   Falls eBay einen Header mit dem Reset-Zeitpunkt mitschickt, erübrigt sich alles Weitere —
   dann wird dieser Wert verwendet statt der Konfiguration.
2. **Aktiv, nach vollständiger Implementierung.** Kontingent gezielt ausschöpfen und danach
   pollen, wann der erste Call wieder durchgeht.
   Das beantwortet die Frage eindeutig, **kostet aber ein volles Tagesbudget** und gehört deshalb
   in eine ruhige Phase, nicht in den laufenden Betrieb.

Bis zur Klärung gilt: der konfigurierte Zeitpunkt steuert den Reset, und die Absicherung gegen
eine falsche Hypothese ist 11.3.1 — liegt der Reset später als angenommen, meldet eBay das
Kontingent als erschöpft, und der Tag wird wieder geschlossen.
Ein falsch geratener Reset-Zeitpunkt führt damit zu ein paar vergeblichen Calls, nicht zu einem
kaputten Feature.

### 11.6 Technische Verortung

Beides ist für dieses Projekt Neuland (siehe Abschnitt 2):
ein **Per-User-Limit auf eingehenden Endpunkten existiert nirgends**,
und der vorhandene `RateLimiter` (`shared/platform/outbound/RateLimiter.java`)
drosselt nur ausgehende Requests global — er kennt weder Nutzer noch Tagesbudgets.
Per-User- und globaler Tageszähler sind daher neu zu bauen.
Natürlicher Ort ist die Anwendungsschicht des neuen Kontexts `purchaseoffers`
(`TitleOfferService`, Phase 1), wo bereits In-Flight-Deduplizierung und Circuit Breaker angesiedelt sind.

**Zur Persistenz:** die laufenden Zähler dürfen im Speicher liegen — ein Neustart verliert dann
etwas Verbrauchsinformation, was durch 11.3.1 abgefangen wird.
Der **Erschöpfungszustand** dagegen muss in die Datenbank (11.3.1),
sonst hebt jeder Deploy die Tagessperre auf.
Das erfordert ein Liquibase-Changelog und schmale Tabellen — die einzige Persistenz in diesem
Feature, und ausdrücklich keine für Preise oder Angebote.
Die Aufteilung auf zwei Tabellen (global je Kontingenttag, Verbrauch je Kontingenttag und Nutzer)
und die Begründung, warum die Per-User-Zähler **nicht** als Spalten an `AppUser` hängen,
stehen in [ADR-0017](adr/0017-quota-verwaltung-fuer-die-ebay-browse-api.md).

**Aufbewahrung:** eine Aufräumregel für die Per-User-Tabelle ist bewusst auf eine spätere
Ausbaustufe verschoben — die Nutzungszahlen sind retrospektiv aufschlussreich, und bei fünf
Nutzern sind es rund 1.800 Zeilen im Jahr.
Zu bedenken bleibt, dass es benutzerbezogene Nutzungsdaten ohne Löschfrist sind und dass beim
Löschen eines Nutzers dessen Zeilen mitgehen sollten.
Die Nutzerzahl kommt über einen bestehenden bzw. schmal zu ergänzenden `port.in` des `accountaccess`-Kontexts,
nicht über einen Direktzugriff auf dessen Datenbestand (ADR-0014).
