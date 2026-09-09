# 0019. `port.spi` für umgekehrte Kontextabhängigkeiten statt Ablage in `shared`

- **Date**: 2026-09-05
- **Status**: Accepted (2026-09-09 nachgetragen — umgesetzt und im Betrieb)

## Context

[ADR-0014](0014-backend-nach-bounded-contexts-und-ports-adaptern.md) ordnet das Backend nach
Bounded Contexts mit `port.in` (was andere bei uns aufrufen dürfen) und `port.out` (unsere eigenen
Abhängigkeiten auf Datenbank und Fremdsysteme). `ArchitectureTest` erzwingt je Kontext, dass von
außen nur `port.in` erreichbar ist.

Beim Bau der eBay-Preisabfrage fiel auf, dass zwischen `accountaccess` und `titlecatalog` ein
**Zyklus** bestand:

- `MeApiController` (accountaccess) brauchte `titlecatalog.port.in.PosterAttributionPort`, um
  `MeDto` das Flag „TMDB-Attributionshinweis anzeigen" mitzugeben.
- `ImdbSearchApiController` (titlecatalog) braucht `accountaccess.port.in.CurrentUserPort`.

Beide Kanten liefen über *veröffentlichte* Ports, waren also nach den bestehenden Regeln zulässig.
Die Regeln haben den Kreis dennoch nicht gemeldet, und das ist keine Nachlässigkeit, sondern
strukturell: jede Isolationsregel prüft **eine** Richtung. Ein Zyklus besteht aus zwei
je für sich erlaubten Kanten.

Der erste Reparaturversuch war, `PosterAttributionPort` nach `shared/platform/api` zu verschieben.
Das macht die Regel grün, weil `shared` von den Isolationsregeln ausgenommen ist — aber es
**beseitigt die Kopplung nicht, es versteckt sie**. Konsequent zu Ende gedacht landet auf diesem Weg
jedes Interface in `shared`, sobald es unbequem wird, und `shared` verkommt vom Ort
kontextübergreifender Bausteine zum Ablagefach für ungelöste Abhängigkeiten. Der Versuch wurde
deshalb verworfen.

Das eigentliche Problem ist die **Richtung** der Abhängigkeit, nicht ihr Ort:
`accountaccess` weiß nichts über Posterquellen und soll es auch nicht. Es hat einen *Bedarf*
(„ein Flag für `/api/me`"), den ein anderer Kontext decken kann.

## Decision

Ein Kontext, der etwas braucht, was ein anderer Kontext liefern kann, **deklariert dafür ein eigenes
Interface und lässt es vom anderen implementieren.** Solche Interfaces liegen in
`<kontext>/port/spi/` und sind veröffentlicht.

Damit hat jeder Kontext drei Port-Arten mit klar verschiedener Bedeutung:

| Paket | Bedeutung | Von außen sichtbar? |
| --- | --- | --- |
| `port.in` | Was andere bei uns **aufrufen** dürfen | ja |
| `port.spi` | Was andere für uns **implementieren** dürfen | ja |
| `port.out` | Unsere eigene Abhängigkeit auf DB/Fremdsystem | **nein** |

Konkret umgesetzt:

- `accountaccess/port/spi/PosterAttributionProvider` deklariert den Bedarf.
- `TmdbProperties` (titlecatalog) implementiert ihn — der Kontext, der weiß, welche Posterquelle
  aktiv ist.
- Die Abhängigkeit zeigt damit nur noch `titlecatalog → accountaccess`, in derselben Richtung wie
  die bereits bestehende über `CurrentUserPort`. Der Zyklus ist weg, nicht umbenannt.

Ergänzend erzwingt `ArchitectureTest` jetzt **Zyklenfreiheit zwischen den Kontexten**
(`bounded_contexts_are_free_of_cycles`). `shared` ist dort in beiden Richtungen ausgeklammert:
`ApiExceptionHandler` bildet die Exception-Typen aller Kontexte ab, `shared` hängt also
zwangsläufig an allen und alle an `shared`. Ohne diese Ausnahme wäre die Regel dauerhaft rot und
damit wertlos.

**Wann `shared` trotzdem richtig ist:** für Bausteine, die keinem Kontext gehören und keine
Richtung haben — Wertetypen des Shared Kernel (`ImdbId`), technische Querschnittsdienste
(`TimeService`, `RateLimiter`, `HttpClientFactory`). Das Unterscheidungsmerkmal ist nicht die
Bequemlichkeit, sondern die Frage, ob es einen natürlichen Eigentümer gibt. `PosterAttributionProvider`
hat einen: den Kontext, der den Wert braucht.

## Consequences

**Was besser wird**

- **Die Kopplung ist sichtbar und gerichtet.** Ein Leser sieht an `port.spi`, dass hier ein anderer
  Kontext etwas beisteuert, und an welchem Ende der Bedarf entsteht.
- **`shared` bleibt klein.** Es gibt jetzt eine benannte Alternative für den Fall, der es sonst
  hätte wachsen lassen.
- **Zyklen werden gemeldet**, und zwar von einer Regel, die verifiziert fehlschlägt, wenn man eine
  Kante wieder einzieht.
- Der Dependency-Inversion-Gedanke steht damit einmal aufgeschrieben und muss nicht bei jedem
  ähnlichen Fall neu hergeleitet werden.

**Was schwieriger wird**

- **Eine dritte Port-Art ist eine Begriffslast.** Wer `port.in` und `port.out` kennt, muss
  `port.spi` dazulernen, und die Grenze zu `port.out` ist erklärungsbedürftig — beide sind formal
  „ausgehend", nur der Implementierer unterscheidet sich.
- **Der Nutzen steht und fällt mit der Benennung.** Ein `port.spi`-Interface, das nach dem
  liefernden Kontext benannt ist statt nach dem Bedarf, hat die Abhängigkeit nur umgedreht, nicht
  entkoppelt: der Name würde weiterhin Wissen über den anderen Kontext transportieren.
  `PosterAttributionProvider` ist an dieser Stelle schon grenzwertig — „Poster" ist Vokabular des
  liefernden Kontexts.
- **Die Zyklusregel kann `shared` nicht prüfen.** Innerhalb von `shared` bleibt alles ungeprüft,
  und ein Kontext, der eine Abhängigkeit über `shared` leitet, umgeht die Regel weiterhin. Die
  Regel schützt vor Versehen, nicht vor Absicht.
- **Ein Interface allein macht noch keine Entkopplung.** Wenn ein Kontext fünf `port.spi`-Einträge
  sammelt, ist das ein Hinweis darauf, dass die Kontextgrenze falsch liegt, und nicht ein Erfolg
  dieses Musters.

## Alternatives Considered

**Interface nach `shared` verschieben.**
Der erste Versuch. Kostet eine Datei-Verschiebung, macht die Regel grün und ändert an der Kopplung
nichts. Verworfen, weil es den Zyklus aus dem Blickfeld nimmt statt ihn aufzulösen, und weil dieses
Vorgehen `shared` planmäßig zum Sammelbecken macht.

**Das Flag aus `/api/me` herausnehmen und als eigenen Endpunkt von `titlecatalog` anbieten.**
Konzeptionell die sauberste Lösung: `/api/me` sammelt heute Daten mehrerer Kontexte, und genau
daraus entstand die Abhängigkeit. Verworfen für diesen Schritt, weil es den API-Vertrag ändert und
Frontend-Arbeit plus einen zusätzlichen Bootstrap-Request nach sich zieht — unverhältnismäßig für
ein einzelnes boolesches Flag. Bleibt die richtige Antwort, falls `MeDto` weitere Fremdfelder
ansammelt.

**Interface in `accountaccess/port/out` legen und in `ArchitectureTest` einzeln ausnehmen.**
Wäre ohne neues Konzept ausgekommen, nach dem Vorbild der Ausnahme für
`ImdbEntry`/`WatchlistDate`. Verworfen, weil es die Bedeutung von `port.out` aufweicht: dort stehen
Abhängigkeiten, die niemanden außerhalb angehen, und eine Ausnahme pro Sonderfall hätte diese
Aussage Stück für Stück entwertet.

**Registrierung zur Laufzeit statt Interface** (titlecatalog meldet den Wert bei accountaccess an).
Verworfen als veränderlicher globaler Zustand mit Initialisierungsreihenfolge als zusätzlichem
Risiko — für einen Wert, den ein Interface statisch und nachvollziehbar liefert.
