# 0018. Auf Hibernates Dirty Checking stützen statt explizitem `save()` für geladene Entitäten

- **Date**: 2026-09-05
- **Status**: Accepted (2026-09-09 nachgetragen — umgesetzt und im Betrieb)

## Context

Die Anwendung nutzt Spring Data JPA. Transaktionen liegen an den Methoden der Anwendungsschicht
(`@Transactional`), und [ADR-0011](0011-kein-open-session-in-view.md) hat Open-Session-in-View
abgeschaltet — der Persistenzkontext endet also **genau an der Transaktionsgrenze** und nicht erst
beim Rendern der Antwort.

Damit gilt die JPA-Grundregel ohne Einschränkung: eine Entität, die **innerhalb** einer Transaktion
geladen wurde, ist *managed*. Hibernate vergleicht sie beim Commit gegen ihren Ladezustand und
schreibt Änderungen selbst. Ein `repository.save(entity)` ist in diesem Fall keine Anweisung,
sondern eine Wiederholung dessen, was ohnehin geschieht.

Für eine **neu konstruierte** Entität gilt das Gegenteil: sie ist *transient*, Hibernate kennt sie
nicht, und ohne ein explizites `save()` verschwindet sie folgenlos.

Der Punkt kam bei der Quota-Verwaltung für die eBay-Anbindung
([ADR-0017](0017-quota-verwaltung-fuer-die-ebay-browse-api.md)) auf und ist bisher **nicht
entschieden**. Die Ist-Lage im Code ist uneinheitlich, mit deutlichem Übergewicht auf der
redundanten Variante:

- `UserPreferencesService.update(...)` lädt einen `AppUser`, mutiert ihn über einen `Consumer` und
  ruft anschließend `users.save(user)` — innerhalb einer `@Transactional`-Methode.
- Dasselbe Muster in `UserAdminService.deactivate(...)`, `PosterService` (zwei Stellen),
  `TitleMetaService` und `WatchlistImportService`.
- Daneben stehen die legitimen Fälle: `AdminUserSeeder`, `GoogleOidcUserService`,
  `StreamInfoService` und die `of(...)`-Zweige in `PosterService`/`TitleMetaService` speichern
  **neu erzeugte** Entitäten, wo `save()` zwingend ist.

Ohne Festlegung entscheidet das jede Änderung neu. Das ist nicht bloß Geschmack: der redundante
Aufruf ist **irreführend**, weil er suggeriert, das Schreiben hinge an ihm — und wer das glaubt,
zieht daraus falsche Schlüsse, sobald eine Entität einmal *detached* ist.

## Decision

**Innerhalb einer Transaktion geladene Entitäten werden mutiert und nicht gespeichert.
Das Schreiben übernimmt Hibernates Dirty Checking beim Commit.**

Verbindlich im Einzelnen:

1. **Kein `save()` für eine managed Entität.**
   Wurde die Entität in derselben Transaktion über ein Repository geladen, genügt die Mutation.
2. **`save()` bleibt Pflicht für transiente Entitäten.**
   Alles, was mit `new` bzw. einer `of(...)`-Fabrik entsteht, muss explizit gespeichert werden,
   sonst ist es verloren.
3. **Die Transaktion muss schreibend sein.**
   Mutierende Methoden tragen `@Transactional`; `@Transactional(readOnly = true)` ist Lesemethoden
   vorbehalten und unterdrückt den Flush.
4. **Laden und Mutieren gehören in dieselbe Transaktion.**
   Eine über eine Transaktionsgrenze hinweg gereichte Entität ist detached; Änderungen daran
   verfallen still. Wo das unvermeidbar ist, wird es kommentiert und explizit behandelt
   (`merge`/`save`), nicht dem Zufall überlassen.
5. **Der Verzicht wird dort kommentiert, wo er nicht offensichtlich ist.**
   Eine Mutation ohne folgenden Repository-Aufruf sieht im Review nach einem vergessenen Aufruf
   aus. Ein knapper Hinweis („managed, Dirty Checking schreibt beim Commit") kostet eine Zeile und
   erspart die Rückfrage.

### Anwendung auf den Bestand

Neuer Code folgt der Regel ab sofort. Die sechs bestehenden Fundstellen werden **nicht in einem
Zug** umgestellt, sondern nach der Pfadfinder-Konvention: wer eine dieser Methoden ohnehin anfasst,
räumt sie mit auf — in einem eigenen Commit, getrennt vom fachlichen Anlass.

Grund für dieses Vorgehen: die Umstellung ist **nicht rein mechanisch**. Bei jeder Fundstelle ist zu
prüfen, ob die Entität wirklich in derselben Transaktion geladen wurde. Ein pauschales Entfernen
aller `save()`-Aufrufe würde genau die Fälle mitreißen, in denen der Aufruf trägt.

## Consequences

**Was besser wird**

- **Der Code sagt die Wahrheit.** Ein `save()` steht künftig nur dort, wo ohne ihn nichts
  geschrieben würde. Das macht die Einfügepfade sichtbar, statt sie im Rauschen untergehen zu
  lassen.
- **Weniger überflüssige Datenbankarbeit.** Bei Entitäten mit *vergebenem* statt generiertem
  Schlüssel — im Projekt etwa `QueryMeta`, `TitlePoster`, `TitleMeta` und die Quota-Tabellen aus
  ADR-0017 — entscheidet Spring Datas `save()` über `isNew()` und nimmt für eine bereits geladene
  Entität den `merge`-Zweig. Das ist ein zusätzlicher Aufruf ohne Nutzen.
- **Eine Frage weniger pro Review.** Die Regel ist kurz und nachschlagbar.

**Was schwieriger wird — und das ist der ernste Teil**

- **Ein Fehler ist still.** Wer die Regel anwendet, aber die Entität außerhalb der Transaktion
  geladen hat, verliert die Änderung **ohne Exception und ohne Logeintrag**. Der explizite
  `save()`-Aufruf hätte diesen Fehler abgefangen. Wir tauschen also Klarheit gegen eine Fehlerklasse,
  die schwerer zu bemerken ist — bewusst, weil ADR-0011 die Transaktionsgrenzen bereits eng und
  explizit gemacht hat.
- **Mockito-Tests können das nicht prüfen.** Ein Unit-Test mit gemocktem Repository sieht kein
  Dirty Checking; er kann nur bezeugen, dass **kein** `save()` erfolgte, nicht dass geschrieben
  wurde. Wo das Schreiben selbst die Zusage ist, braucht es einen Test gegen eine echte
  Persistenzschicht.
- **Der Bestand bleibt eine Weile uneinheitlich.** Zwei Muster nebeneinander sind für Lesende
  verwirrender als ein durchgehend redundantes. Das ist der Preis des Pfadfinder-Vorgehens; die
  Alternative wäre eine große, riskante Sammeländerung.
- **Automatisch erzwingbar ist die Regel nicht.** Ob eine Entität managed ist, steht nicht im
  Bytecode. Eine ArchUnit-Regel könnte allenfalls `save()`-Aufrufe zählen, nicht sie beurteilen.
  Diese Regel lebt vom Review.

## Alternatives Considered

**Weiterhin überall explizit `save()` aufrufen.**
Der Status quo an fünf von sechs Fundstellen, und nicht ohne Argument: der Aufruf ist defensiv,
macht die Schreibabsicht sichtbar und funktioniert auch dann noch, wenn eine Entität wider Erwarten
detached ist. Verworfen, weil er genau dadurch **falsche Sicherheit** stiftet: er suggeriert, das
Persistieren hinge an ihm, und verschleiert den Unterschied zwischen managed und detached — der
Unterschied, auf den es tatsächlich ankommt. Wer nie gelernt hat, dass Dirty Checking existiert,
schreibt irgendwann Code, der auf `save()` eines detached Objekts vertraut und dabei stillschweigend
ein Objekt ohne Versionsprüfung überschreibt.

**Regel umdrehen: immer `save()`, dafür Dirty Checking abschalten.**
Technisch über `@org.hibernate.annotations.Immutable` oder ein eigenes Flush-Regime denkbar.
Verworfen als Kampf gegen den Persistenzanbieter: Dirty Checking ist kein Zusatz von JPA, sondern
sein Kern. Es abzuschalten hieße, einen ORM zu benutzen und ihn zugleich zu verweigern.

**Explizites `flush()` statt `save()`.**
Verworfen: `flush()` steuert den *Zeitpunkt* des Schreibens, nicht ob geschrieben wird. Es zur
Absichtserklärung umzudeuten wäre ein weiteres irreführendes Signal — mit dem Zusatzschaden, dass
vorzeitiges Flushen Sperren früher hält als nötig.

**Die Regel per ArchUnit erzwingen.**
Verworfen, weil nicht entscheidbar: ob ein `save()`-Argument managed oder transient ist, ergibt sich
aus dem Kontrollfluss, nicht aus der Struktur. Eine Regel, die alle `save()`-Aufrufe in der
Anwendungsschicht verbietet, würde die notwendigen Einfügepfade mit verbieten.
