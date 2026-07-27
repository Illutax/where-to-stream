# 0011. Kein Open-Session-in-View, kein Lazy Loading

- **Date**: 2026-07-27
- **Status**: Accepted

## Context

Spring Boot aktiviert **Open-Session-in-View (OSIV)** standardmäßig
(`spring.jpa.open-in-view=true`) und warnt beim Start, man solle die Einstellung explizit setzen.
OSIV bindet einen `EntityManager` für die **gesamte** Request-Dauer an den Thread (bis die Antwort
gerendert ist), damit Lazy-Associations auch außerhalb der Service-Schicht (z. B. beim Rendern)
nachgeladen werden können, ohne `LazyInitializationException`.

Für diese Anwendung passt das nicht:

- Seit ADR-0008 ist das UI eine **SPA mit JSON-API** — es gibt kein serverseitiges View-Rendering,
  Controller liefern **DTOs**, nie Entities. Die JSON-Serialisierung fasst den Persistence-Context
  also nie an.
- Alle Associations sind bereits **`FetchType.EAGER`** (`AppUser`-Rollen, `QueryResultDB`,
  `QueryMeta.getQueries()`) — es gibt nichts, was lazy nachzuladen wäre.
- OSIV hält Persistence-Context (und potenziell eine DB-Connection) länger als nötig am Request.
  Genau diese Art „Connection über den ganzen Request halten" hat gerade den Hikari-Pool erschöpft,
  als der `PosterService` innerhalb einer Transaktion Netzwerk-I/O machte — wir wollen DB-Arbeit
  bewusst **innerhalb der Service-/Transaktionsgrenzen** halten.

## Decision

**`spring.jpa.open-in-view=false`** (explizit gesetzt).

Dazu die Design-Regel: **Kein Lazy Loading.** Associations sind `EAGER` (oder werden im Query
explizit per Fetch-Join geladen); jeder DB-Zugriff geschieht innerhalb einer `@Transactional`
Service-Methode; Entities werden **nicht** über die Schichtgrenze/an die Serialisierung gereicht —
die Presentation-Schicht sieht nur DTOs. Was eine Antwort braucht, wird in der Service-Schicht
vollständig geladen und als DTO herausgegeben.

## Consequences

**Einfacher / besser:**

- DB-Connections werden nur innerhalb der Transaktionen gehalten, nicht über den ganzen Request —
  konsistent mit der Connection-Disziplin aus dem Poster-Fix.
- **Fail-fast:** Fügt jemand später eine `LAZY`-Association hinzu und serialisiert sie außerhalb
  einer Transaktion, gibt es sofort eine klare `LazyInitializationException` statt eines stillen
  Extra-Zugriffs während des Renderings.
- Die Start-Warnung ist weg; die Entscheidung ist dokumentiert statt geerbt.

**Schwieriger / Nachteile:**

- Man muss in der Service-Schicht **bewusst alles laden**, was das DTO braucht (kein bequemes
  Nachladen „später"). Bei den aktuellen, kleinen EAGER-Collections ist das unkritisch.
- `EAGER` überall lädt immer die ganze Association mit. Solange die Collections klein bleiben
  (Rollen, Query-Ergebnisse pro Titel), ist das in Ordnung; wächst eine Association stark, ist die
  richtige Antwort ein gezielter Fetch-Join im Repository — **nicht** OSIV wieder einzuschalten.

## Alternatives Considered

- **OSIV anlassen (`true`) und nur die Warnung stummschalten:** verworfen — bringt für eine
  DTO-basierte JSON-API keinen Nutzen, hält den Persistence-Context länger und verdeckt
  versehentliche DB-Zugriffe außerhalb von Transaktionen.
- **Lazy Loading einführen + auf OSIV stützen:** verworfen — koppelt das Laden an das Rendering und
  reicht Entity-Proxies bis in die Serialisierung; wir wollen die Ladelogik explizit in der
  Service-Schicht. Wächst eine Collection, wird gezielt per Fetch-Join geladen.
