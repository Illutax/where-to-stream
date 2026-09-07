# 0017. Quota-Verwaltung für die eBay Browse API

- **Date**: 2026-09-05
- **Status**: Superseded (2026-09-07)

> **Gegenstandslos.** Der eBay-Developer-Account, auf dem diese Entscheidung ruht, wurde nie
> freigeschaltet — die Browse API hat nie eine Antwort geliefert, und die hier beschriebene
> Quota-Verwaltung ist nie in Betrieb gegangen.
> Die Preisabfrage ist mit TODO-56 zurückgebaut, ihre beiden Tabellen sind per Changeset entfernt.
>
> An ihre Stelle tritt ein Suchlink je Titel (TODO-57), der im Browser entsteht:
> keine Anfrage, kein Budget, kein Account — und damit nichts mehr zu rationieren.
> Die Frage, die diese ADR beantwortet, stellt sich nicht mehr.
>
> Das Dokument bleibt, weil die Überlegungen zur Aufteilung eines geteilten Kontingents
> und zum Vorrang der Auskunft des Anbieters vor dem eigenen Zähler wiederverwendbar sind,
> falls je wieder eine kontingentierte fremde API dazukommt.

## Context

Das geplante Feature „eBay-Preisabfrage pro Titel" (siehe
[`docs/EBAY_PRICE_LOOKUP_PLAN.md`](../EBAY_PRICE_LOOKUP_PLAN.md)) holt zu einem Titel den
günstigsten Sofortkauf und das günstigste laufende Auktionsgebot.
Als Quelle war zunächst das Scrapen der eBay-Suchergebnisseite vorgesehen.
Ein POC hat diese Variante widerlegt: nach rund 40 Requests in 25 Minuten war die Egress-IP
marktplatzübergreifend gesperrt, und Abrufe im Minutenabstand liefen ausnahmslos gegen eine
Akamai-Challenge, ohne dass sich der Zugang erholte.
Die Anbindung erfolgt deshalb über die **eBay Browse API**.

Damit tauscht das Projekt ein Blockierungsrisiko gegen eine harte, aber planbare Grenze:

- Das Standardkontingent liegt bei **5.000 Calls pro Tag und Applikation** — nicht pro Benutzer.
  Alle Nutzer teilen sich dieses Budget.
- Eine Titelabfrage kostet nach heutigem Stand **zwei** Calls
  (`buyingOptions:{FIXED_PRICE}` und `buyingOptions:{AUCTION}`).
  Ob sich beide in einem Call kombinieren lassen, ist unverifiziert; bis dahin gilt Faktor 2.
- Mehr Kontingent gibt es nur über eBays „Application Growth Check".

Ohne Bremse ist dieses Budget schnell erschöpft: eine einzelne Watchlist mit 200 Titeln
verbraucht bei „alles einmal abfragen" 400 Calls, also 8 % des Tagesbudgets für alle Nutzer
zusammen.

Der Ist-Zustand des Projekts bietet dafür **keine** Grundlage:

- Ein **Per-User-Rate-Limit auf eingehenden API-Endpunkten existiert nirgends**.
- Der vorhandene `RateLimiter` (`shared/platform/outbound/RateLimiter.java`) ist ein reiner
  Abstandshalter zwischen *ausgehenden* Requests einer Integration
  (`acquire()` blockiert bis zum nächsten Slot).
  Er kennt weder Benutzer noch Tagesbudgets und ist für diese Aufgabe nicht erweiterbar, ohne
  seinen Zweck zu verwässern.
- `TimeService` (`shared/platform/time`, [ADR-0003](0003-zeit-ueber-timeservice-facade.md)) liefert
  `now()` und `today()`, wobei `today()` die **System-Zeitzone** verwendet.
  Ein eBay-Kontingenttag ist aber kein Tag in der Systemzone (siehe unten).

Zwei Vorgaben des Auftraggebers sind gesetzt:

1. **Aufteilungsformel** `requests_pro_nutzer = 5000 / (anzahl_der_nutzer / 2)`, also `10000 / n`.
   Das ist eine **bewusste doppelte Überbuchung** unter der Annahme, dass höchstens die Hälfte der
   registrierten Nutzer an einem Tag Preise abruft.
   Sie tauscht ungenutztes Kontingent gegen ein höheres Per-User-Limit.
   Zur Einordnung: die Anwendung hat derzeit **fünf** registrierte Benutzer,
   und die Zahl wird absehbar nicht deutlich wachsen.
2. **Globaler Deckel exakt bei 5.000**, ohne Sicherheitsabschlag.

Aus der Überbuchung folgt zwingend, dass ein Per-User-Limit allein das Kontingent **nicht**
schützt: schöpfen mehr als die Hälfte der Nutzer ihr Limit aus, brauchen die ersten aktiven Nutzer
das gemeinsame Budget auf.

Ungeklärt ist schließlich, **wann eBay das Tageskontingent zurücksetzt**.
Die Arbeitshypothese lautet „12 Uhr Pazifik-Zeit", stammt aus einer Gemini-Auskunft und ist durch
keine Primärquelle belegt.
Sie ist zudem doppeldeutig: „12 Uhr" kann Mittag oder Mitternacht meinen.
Erschwerend wechselt die Pazifik-Zeit zwischen PST (UTC−8) und PDT (UTC−7).

## Decision

Wir bauen die Quota-Verwaltung als **eigenständigen Bestandteil des neuen Bounded Context
`purchaseoffers`**, mit zwei Zählerebenen, eigener Persistenz und einer klaren Vorrangregel
gegenüber eBay.

### 1. Zwei Zählerebenen

- **Per-User-Tageszähler**: `10000 / n` Requests je Benutzer und Kontingenttag.
- **Globaler Tageszähler**: harter Deckel bei **5.000** Requests je Kontingenttag,
  der greift, bevor eBay selbst ablehnt.

`n` ist die Anzahl registrierter Benutzer laut Datenbank,
**einmal je Kontingenttag ermittelt und für diesen Tag eingefroren**.
Benutzer, die im Laufe des Tages hinzukommen, erhalten sofort dasselbe Limit wie alle anderen;
bestehende Limits werden nicht neu berechnet.

Die Benutzerzahl wird über einen `port.in` des `accountaccess`-Kontexts bezogen,
nicht über einen Direktzugriff auf dessen Datenbestand
([ADR-0014](0014-backend-nach-bounded-contexts-und-ports-adaptern.md)).

### 2. eBays Antwort schlägt den eigenen Zähler

Unser globaler Zähler ist eine **Schätzung** des Verbrauchs, eBays Kontingentstand ist die
**Tatsache**.
Beide laufen auseinander — durch Retries, durch parallele Requests, durch Calls, die unterwegs
scheitern, und durch jeden Neustart der Anwendung.

Meldet eBay Kontingenterschöpfung, gilt der Kontingenttag **sofort als beendet**,
unabhängig vom eigenen Zählerstand.
Ab diesem Punkt geht bis zum nächsten Reset kein Call mehr nach draußen.

Genau dieser Vorrang ersetzt den Sicherheitsabschlag beim globalen Deckel:
wer sich verzählt, merkt es an der Quota-Antwort statt an einem Puffer.

### 3. Persistenz in zwei eigenen Tabellen — nicht am Benutzer

Der Quota-Zustand wird persistiert, und zwar in **zwei schmalen Tabellen im
`purchaseoffers`-Kontext**:

- eine Zeile je Kontingenttag für den globalen Verbrauch und den Erschöpfungszeitpunkt,
- eine Zeile je Kontingenttag und Benutzer für den Per-User-Verbrauch.

Ausdrücklich **nicht** als Spalten an `AppUser`:
der Benutzer gehört zu `accountaccess`, das Quota-Budget zu `purchaseoffers`.
Eine Spalte am Benutzer würde Zustand des einen Kontexts in die Entität des anderen legen,
die Isolationsregel aus ADR-0014 verletzen und die Benutzertabelle bei jeder Preisabfrage
beschreiben.

Persistiert wird, weil der Zustand einen Neustart überleben muss:
ein Deploy am Abend würde sonst die Tagessperre aufheben, und die Anwendung liefe erneut gegen ein
bei eBay längst erschöpftes Kontingent.
Dasselbe Argument gilt für die Per-User-Zähler — würden nur sie im Speicher liegen, verschenkte
jeder Deploy allen Benutzern ein frisches Budget, und der globale Deckel wäre der einzige reale
Schutz.

Das ist eine **bewusste Ausnahme** von der Festlegung des Plans, für dieses Feature nichts zu
speichern.
Diese Festlegung gilt **Preisdaten**: ein Gebot ändert sich minütlich, und ein Sofortkaufangebot
mit Stückzahl 1 ist nach dem Verkauf ganz weg — ein persistierter Preis wäre nicht „etwas älter",
sondern falsch.
Quota-Zustand ist das Gegenteil: tagesstabil, winzig, und ohne Persistenz wirkungslos.
Für Angebote und Preise bleibt es bei **keiner** Tabelle.

### 4. Der Kontingenttag ist ein Tag in Pazifik-Zeit

Der Kontingenttag wird aus `TimeService.now()` und einer **konfigurierten Zeitzone**
(`America/Los_Angeles`) sowie einer **konfigurierten Reset-Uhrzeit**
(`ebay.quota.reset-time`, Default `00:00`) abgeleitet.

- **Nicht** `LocalDate.now()` — das verbietet ADR-0003.
- **Auch nicht** `TimeService.today()`: das liefert den Tag in der Systemzone,
  und der Kontingenttag ist keiner.
  `TimeService` bleibt unverändert; die Zonenlogik gehört in `purchaseoffers`.
- **Zone statt Offset:** ein fest verdrahtetes UTC−8 läuft während der Sommerzeit eine Stunde
  daneben — und zwar genau so, dass ein zu früher Reset gegen ein noch erschöpftes Kontingent
  läuft.
- **Uhrzeit konfigurierbar,** weil die Hypothese „12 Uhr" doppeldeutig ist.
  Ist die Lesart falsch, kostet die Korrektur einen Konfigurationswert statt eines Releases.

Falls die Browse API einen Header mit dem Reset-Zeitpunkt mitschickt, hat dieser Vorrang vor der
Konfiguration.

### 5. Erschöpfung degradiert sichtbar, sie wirft nicht

Weder ein erreichtes Per-User-Limit noch ein erschöpftes globales Budget führt zu einer Exception
im Benutzer-Request.
Der Endpunkt antwortet regulär mit einem Zustand „Budget erschöpft", das Frontend zeigt eine
verständliche Meldung — analog zum Zustand „derzeit nicht verfügbar".
Per-User- und globale Erschöpfung werden dabei **unterschieden**, weil der Unterschied für den
Benutzer relevant ist.

### 6. Logging als Datenquelle für die offene Frage

Bei jeder Kontingenterschöpfung durch eBay werden Statuscode, Fehlercode, Fehlertext und
**alle** von eBay mitgeschickten Kontingent-Header auf `warn` protokolliert.
Das dient nicht der Fehlersuche, sondern der empirischen Klärung des Reset-Zeitpunkts:
zwei solche Ereignisse zusammen mit dem jeweils nächsten erfolgreichen Call grenzen das
Reset-Fenster ein, ohne irgendetwas zu provozieren.

## Consequences

**Was einfacher wird:**

- Das Kontingent ist gegen Erschöpfung durch einzelne Nutzer geschützt, und zwar serverseitig —
  clientseitiges Limiting wäre wirkungslos gewesen.
- Die Tagessperre übersteht Deploys und Neustarts.
  Ein Abend-Deploy kann das Kontingent nicht mehr versehentlich „freigeben".
- Ein falsch geratener Reset-Zeitpunkt ist folgenlos für die Korrektheit:
  liegt der Reset später als angenommen, meldet eBay das Kontingent als erschöpft und der Tag wird
  wieder geschlossen.
  Es kostet ein paar vergebliche Calls, nicht das Feature.
- Die Quota-Logik liegt vollständig in `purchaseoffers` und ist damit ArchUnit-prüfbar isoliert.
- Per-User-Limits sind erklärbar, weil sie sich innerhalb eines Tages nicht ändern.

**Was schwieriger wird:**

- **Das Projekt bekommt sein erstes eingehendes Rate-Limiting.** Das ist neuer, eigenständig zu
  testender Code — inklusive der Nebenläufigkeit zweier Zähler.
- **Es gibt Persistenz in einem Feature, das ausdrücklich keine haben sollte.** Der Unterschied
  zwischen „Preisdaten nicht speichern" und „Quota-Zustand speichern" muss verstanden werden,
  sonst wirkt es wie ein Widerspruch. Dieses ADR ist der Ort, an dem er festgehalten ist.
- **Die Per-User-Tabelle wächst ohne Begrenzung.**
  Eine Aufräumregel ist bewusst auf eine spätere Ausbaustufe verschoben:
  die Nutzungszahlen sind retrospektiv aufschlussreich — wie oft die Annahme aus der Formel reißt,
  wie sich der Verbrauch über die Benutzer verteilt, ob der globale Deckel überhaupt je greift.
  Bei `n` = 5 sind das rund 1.800 Zeilen im Jahr, also kein Größenproblem.
  Zwei Punkte bleiben dennoch zu bedenken:
  es handelt sich um benutzerbezogene Nutzungsdaten ohne Löschfrist,
  und beim Löschen eines Benutzers sollten dessen Zeilen mitgehen.
- **Die Überbuchung bleibt eine Wette.** Hält die Annahme aus der Formel nicht, greift der globale
  Deckel — und dann ist das Feature für alle aus, nicht nur für die Vielnutzer.
  Wie oft das passiert, muss beobachtet werden.
- **Die Zeitzonenlogik bleibt zu testen, ist in ihrer Wirkung aber entschärft.**
  Ein Fehler wirkt sich nur zweimal im Jahr und nur für eine Stunde aus, und genau diesen Fall
  fängt der Vorrang von eBays Kontingent-Antwort (Punkt 2) ab:
  ein zu früher Reset kostet einige vergebliche Calls, dann schließt der Tag wieder.
  Sie gehört trotzdem mit fixem Clock gegen beide Zeitzonenzustände getestet — der Test ist billig,
  und ohne ihn fällt ein Fehler mangels Symptom nie auf.
- **Beim aktuellen `n` ist das Per-User-Limit keine Fairness-Bremse.**
  Bei den derzeit fünf registrierten Benutzern ergibt die Formel 2.000 Requests je Benutzer,
  also 1.000 Titelabfragen — schon drei gleichzeitig aktive Vielnutzer erreichen den globalen
  Deckel.
  Der Auftraggeber hat das akzeptiert: die Nutzerzahl ist bekannt und wird absehbar nicht deutlich
  wachsen, und gegen den eigentlich gefürchteten Fall — ein Skript mit gültigem Session-Cookie —
  wirkt das Per-User-Limit unverändert.
  Der Schutz des Kontingents kommt in dieser Konstellation vom globalen Deckel;
  die Aufteilungsformel entfaltet ihren Zweck erst bei größerem `n`.

**Offen, bewusst nicht hier entschieden:**

- Der genaue Fehlercode bei Kontingenterschöpfung (vermutlich `2001`/`RATE_LIMIT`) und welche
  Kontingent-Header die Browse API mitschickt — gegen die Sandbox zu verifizieren.
- Ob beide Angebotsarten in einem Call abfragbar sind.
  Das würde das Titel-Budget verdoppeln, ändert aber nichts an dieser Entscheidung.
- Ob der Reset tatsächlich um 12 Uhr Pazifik-Zeit erfolgt.

## Alternatives Considered

**Per-User-Zähler als Spalten an `AppUser`.**
Naheliegend, weil das Limit „am Benutzer hängt".
Verworfen: `AppUser` gehört zu `accountaccess`, das Quota-Budget zu `purchaseoffers`.
Das hätte die Isolationsregel aus ADR-0014 verletzt, die Benutzertabelle bei jeder Preisabfrage
beschrieben und die Quota-Logik über zwei Kontexte verteilt.
Außerdem wären die Spalten nach einem Wechsel der Angebotsquelle sinnlos, aber schwer wieder
loszuwerden.

**Reine In-Memory-Zähler, keine Persistenz.**
Am billigsten und im Einklang mit „dieses Feature speichert nichts".
Verworfen für den globalen Erschöpfungszustand, weil jeder Neustart die Tagessperre aufhöbe.
Für die Per-User-Zähler wäre es vertretbar gewesen — verworfen aus Konsistenz:
ein Deploy würde sonst allen Benutzern ein frisches Budget schenken und den globalen Deckel zum
einzigen realen Schutz machen.

**Nur ein globaler Deckel, kein Per-User-Limit.**
Deutlich weniger Code.
Verworfen, weil ein einzelner Nutzer — oder ein Skript mit gültigem Session-Cookie — dann das
Tagesbudget aller anderen aufbrauchen kann.
Genau dieser Fall ist der Grund für die Aufteilungsformel.

**Sicherheitsabschlag beim globalen Deckel (z. B. 4.800 statt 5.000).**
War der ursprüngliche Vorschlag im Plan.
Vom Auftraggeber verworfen zugunsten des exakten Werts, abgesichert durch den Vorrang von eBays
Antwort (Punkt 2).
Der Abschlag hätte 200 Calls täglich verschenkt, um einen Fall abzudecken, den Punkt 2 ohnehin
abfängt.

**Erweiterung des bestehenden `RateLimiter`.**
Verworfen: der `RateLimiter` hält Abstände zwischen ausgehenden Requests ein und ist bewusst
zustandsarm.
Tagesbudgets, Benutzerbezug und Persistenz hätten ihn zu etwas anderem gemacht und alle vier
bestehenden Integrationen mitbetroffen.
Der ausgehende Abstandshalter bleibt unverändert und kommt für die eBay-Integration zusätzlich zum
Einsatz.

**Bucket4j, Resilience4j oder Redis-gestütztes Limiting.**
Verworfen als unverhältnismäßig: die Anwendung läuft als einzelne Instanz, das Limit ist ein
simpler Tageszähler, und jede dieser Optionen brächte eine Abhängigkeit oder eine
Infrastrukturkomponente für Logik, die aus zwei Zählern und einer Tabelle besteht.
Bei einem Wechsel auf mehrere Instanzen ist diese Entscheidung neu zu bewerten — dann trägt die
DB-Persistenz aus Punkt 3 bereits einen Teil der Last.
