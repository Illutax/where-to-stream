# Architecture Decision Records (ADRs)

Diese ADRs halten fest, **warum** bestimmte Architekturentscheidungen so getroffen wurden
(Kontext, Optionen, Trade-offs) — nicht **wie** das System implementiert ist. Format:
[Nygard-ADR](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions), analog
zum `adr`-Skill der Team-Doku.

| Nr. | Titel | Status |
| --- | --- | --- |
| [0001](0001-hash-routing-fuer-den-angular-client.md) | Hash-Routing für den Angular-Client | Accepted |
| [0002](0002-frontend-build-via-exec-maven-plugin.md) | Frontend-Build über exec-maven-plugin (System-Node) | Accepted |
| [0003](0003-zeit-ueber-timeservice-facade.md) | Zeit über eine TimeService-Facade statt statischer now()-Aufrufe | Accepted |
| [0004](0004-vitest-als-angular-test-runner.md) | Vitest als Test-Runner für den Angular-Client | Accepted |
| [0005](0005-assertj-und-mockito-im-backend.md) | AssertJ (mit Mockito) für Backend-Tests | Accepted |
| [0006](0006-authentifizierung-und-autorisierung.md) | Authentifizierung & Autorisierung (Spring Security) | Accepted |
| [0007](0007-watchlist-pro-benutzer.md) | Watchlist pro Benutzer (DB-gestützt statt globaler Dateiliste) | Accepted |
| [0008](0008-thymeleaf-client-entfernen.md) | Thymeleaf-Client entfernen — SPA-only UI | Accepted |
| [0009](0009-domainvalues-statt-primitiven.md) | Domain-Values statt primitiver Datentypen | Accepted |
| [0010](0010-optionals-nicht-auf-null-defaulten.md) | Optionals nicht auf `null` defaulten (funktional konsumieren) | Accepted |
| [0011](0011-kein-open-session-in-view.md) | Kein Open-Session-in-View, kein Lazy Loading | Accepted |

Neue ADR: nächste freie 4-stellige Nummer, `NNNN-kurzer-slug.md`.
