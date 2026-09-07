# 0020. Admin-Impersonierung über Spring Securitys `SwitchUserFilter`

- **Date**: 2026-09-06
- **Status**: Proposed

## Context

Ein ADMIN soll die Anwendung vorübergehend als ein anderer Benutzer sehen können, um dessen
Meldungen nachzuvollziehen, ohne sich das Passwort geben zu lassen (TODO-53). Das ist kein
Bedienkomfort, sondern ein Eingriff in die Zurechenbarkeit: solange eine Impersonierung läuft, sieht
das System Handlungen unter einer Identität, die sie nicht ausgelöst hat.

Ausgangslage:

- Authentifizierung über Spring Security mit Form-Login, HTTP Basic und optional Google-OIDC
  ([ADR-0006](0006-authentifizierung-und-autorisierung.md)); Sitzungen liegen in der Datenbank.
- Der angemeldete Benutzer wird in der Präsentationsschicht aufgelöst und als `userId` nach unten
  gereicht; darunter liest niemand den `SecurityContext`
  ([ADR-0007](0007-watchlist-pro-benutzer.md), per ArchUnit erzwungen).
- ~~Die eBay-Preisabfrage bucht zwei Calls je Titel auf ein Tagesbudget, das alle Benutzer teilen,
  und rechnet pro Benutzer ab~~ ([ADR-0017](0017-quota-verwaltung-fuer-die-ebay-browse-api.md)) —
  **entfallen (TODO-56).** Diese Ausgangslage gibt es nicht mehr; siehe Punkt 5.

Spring Security bringt `SwitchUserFilter` mit (in 7.1 vorhanden, geprüft). Er tauscht die
Authentifizierung im `SecurityContext` aus und hinterlegt die ursprüngliche als
`SwitchUserGrantedAuthority` — daraus speist sich der Rückweg. Ein Eigenbau müsste dieselbe
Zustandsmaschine nachbilden, inklusive Sitzungsbehandlung und Rückkehr.

## Decision

**Impersonierung über `SwitchUserFilter`, mit vier Einschränkungen, die zusammen den Ausschlag
geben.**

### 1. Kein ADMIN impersoniert einen ADMIN

Wer einen anderen ADMIN übernimmt, kann dessen Rechte nutzen, ohne dass die Handlung als eigene
erkennbar ist — eine Rechteausweitung ohne Spur. Der Ausschluss ist die konservative und prüfbare
Wahl und wird über einen eigenen `UserDetailsChecker` am Filter erzwungen, also an der Stelle, an
der der Zielbenutzer geladen wird, und nicht erst in der Oberfläche.

### 2. Schreiben ist erlaubt

Bewusst gegen die vorsichtigere Variante entschieden: ein Werkzeug, mit dem man einen gemeldeten
Fehler nicht nachstellen kann, löst das Problem nicht, für das es gebaut wird. Viele Meldungen
betreffen genau die schreibenden Wege (Import, Watchlist ändern, Einstellungen).

Der Preis ist real und wird nicht wegdiskutiert: **während einer Impersonierung sind Änderungen im
Datenbestand nicht vom Benutzer selbst zu unterscheiden.** Die Gegenmaßnahmen sind Punkt 3 und 4 —
sie machen den Vorgang nachvollziehbar und sichtbar, sie verhindern ihn nicht.

### 3. Beginn und Ende werden protokolliert, mit beiden Identitäten

Ohne das lässt sich hinterher nicht entscheiden, ob ein Benutzer etwas selbst getan hat. Beide
Ereignisse gehen auf `warn` — nicht weil etwas schiefgeht, sondern weil ein Logeintrag, der
unterhalb der üblichen Schwelle liegt, im Ernstfall nicht mehr da ist.

### 4. Die Oberfläche zeigt es dauerhaft und unübersehbar

Ein Wechsel, den man vergisst, ist der gefährlichere Fehler: der Admin hält seine eigene Sitzung für
die des Benutzers oder umgekehrt. Ein Banner bleibt über alle Seiten stehen, benennt beide
Identitäten und trägt den Ausstieg direkt neben sich. `MeDto` transportiert den Zustand, damit die
SPA ihn kennt, ohne ihn zu erraten.

### 5. Preisabfragen sind während einer Impersonierung gesperrt

> **Hinfällig seit 2026-09-07 (TODO-56).** Die Preisabfrage ist zurückgebaut, weil der
> eBay-Developer-Account nie freigeschaltet wurde; ihr Ersatz ist ein Suchlink, der im Browser
> entsteht und kein Kontingent verbraucht. Damit gibt es keine Stelle mehr, an der Impersonierung
> etwas kostet — die Sperre, der Sonderstatus und der `ImpersonationPort`, den sie brauchte, sind
> ersatzlos entfallen. Die übrigen vier Punkte gelten unverändert.
>
> Der Absatz bleibt stehen, weil die Frage „auf wessen Kontingent bucht eine impersonierte
> Handlung?" beim nächsten kontingentierten Feature wieder auftaucht — und die Antwort dieselbe
> sein dürfte.

Die einzige Stelle, an der Impersonierung Geld kostet. Auf wessen Kontingent gebucht würde, ist eine
Frage ohne gute Antwort: auf den impersonierten Benutzer gebucht verbraucht der Admin fremdes
Budget, und der Benutzer stößt später an ein Limit, das er nicht ausgeschöpft hat; auf den Admin
gebucht verlangt, dass die Quota-Schicht die ursprüngliche Identität kennt — und die kennt sie
nach ADR-0007 bewusst nicht.

Statt eine der beiden Unschönheiten einzubauen, entfällt die Abfrage. Der Endpunkt antwortet mit
einem eigenen Status, den die Oberfläche als Hinweis rendert — kein Fehler, sondern eine erklärte
Nichtverfügbarkeit, wie die übrigen Zustände dieses Endpunkts auch.

Die Prüfung sitzt im Controller, nicht im Service: ob eine Impersonierung läuft, steht in der
Authentifizierung, und die zu lesen ist nach ADR-0007 Sache der Präsentationsschicht.

## Consequences

**Was besser wird**

- Ein Admin kann eine Meldung nachvollziehen, ohne nach dem Passwort zu fragen — der bisherige
  Notbehelf, der schlechter ist als jede Impersonierung.
- Der Weg ist Standardfunktionalität statt Eigenbau; die Zustandsmaschine samt Rückkehr kommt aus
  dem Framework.
- Impersonierungen sind im Log rekonstruierbar.

**Was schwieriger wird**

- **Die Zurechenbarkeit von Datenänderungen sinkt.** Das ist die direkte Folge von Punkt 2 und
  nicht reparabel, nur beobachtbar. Wer im Nachhinein wissen will, wer eine Watchlist geändert hat,
  muss das Log heranziehen; die Daten selbst sagen es nicht.
- **Ein neuer, mächtiger Endpunkt.** Er ist auf ADMIN beschränkt und schließt ADMIN-Ziele aus, aber
  er existiert. Eine übernommene Admin-Sitzung wird damit wertvoller als vorher.
- ~~**Der gesperrte Preisabruf ist eine sichtbare Lücke.**~~ Mit dem Rückbau der Preisabfrage
  (TODO-56) gegenstandslos: es gibt keine Preisanzeige mehr, deren Sperre auffallen könnte.
- **Ein Zustand mehr im Frontend.** Banner und Ausstieg wollen mitgepflegt werden.
  Der dritte Punkt — der Sonderstatus der Preisabfrage — ist mit ihr entfallen.
- **Testbarkeit:** dass ADMIN-Ziele abgelehnt werden, ist eine Zusicherung, die nur ein Test hält —
  im Betrieb fällt ihr Fehlen erst auf, wenn sie gebraucht wird.

## Alternatives Considered

**Nur-Lesen-Impersonierung.**
Die sicherere Variante: jeder schreibende Endpunkt lehnt während einer Impersonierung ab, die
Zurechenbarkeit bleibt unangetastet. Verworfen, weil ein erheblicher Teil der Meldungen genau die
schreibenden Wege betrifft — ein Werkzeug, das den gemeldeten Fehler nicht auslösen kann, erspart
die Rückfrage nach dem Passwort nicht. Die Entscheidung ist umkehrbar: die Sperre wäre ein Filter
über die mutierenden Endpunkte und ließe sich nachrüsten, wenn die Praxis dagegen spricht.

**Eigene Implementierung statt `SwitchUserFilter`.**
Verworfen: dieselbe Zustandsmaschine, selbst geschrieben, mit dem Unterschied, dass Fehler darin
uns gehören. Sicherheitsmechanik nachzubauen, die das Framework mitbringt, ist der teuerste Weg zu
einem schlechteren Ergebnis.

**Admin darf Passwörter zurücksetzen statt zu impersonieren.**
Existiert bereits (`resetPassword`) und ist der Status quo. Verworfen als Ersatz, weil es den
Benutzer aussperrt, um ihm zu helfen — und weil ein zurückgesetztes Passwort dem Admin dauerhaften
Zugang gibt, wo eine Impersonierung eine begrenzte, sichtbare Sitzung ist.

**Impersonierung hinter einem Feature-Flag.**
Erwogen, um die Angriffsfläche in Umgebungen ohne Bedarf zu schließen. Verworfen für dieses
Projekt: bei fünf Nutzern und einer Installation ist ein Flag, das nie umgelegt wird, vor allem ein
Pfad, der nie getestet wird.
