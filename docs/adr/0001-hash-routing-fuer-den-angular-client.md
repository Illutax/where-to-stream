# 0001. Hash-Routing für den Angular-Client

- **Date**: 2026-07-19
- **Status**: Accepted

## Context

Mit der Umstrukturierung von w2s ist neben der bestehenden Thymeleaf-UI ein Angular-Client
(SPA) dazugekommen. Beide Clients laufen aus **einem** Spring-Boot-Fat-JAR:

- Die Thymeleaf-UI belegt die bestehenden Server-Pfade (`/`, `/amazon`, `/prime`, `/disney`,
  `/netflix`, `/wow`, `/google`, `/list`, `/manage`, `/public/status`).
- Die SPA wird unter `/app/` ausgeliefert; ihr Bundle liegt im JAR unter `static/app/`.
- In PROD läuft die Anwendung hinter dem Context-Path `/w2s` (`compose.yml` setzt
  `server.servlet.context-path=/w2s`); lokal (JAR oder `mvn spring-boot:run`) ohne Context-Path
  auf Port 8001; im Frontend-Dev über `ng serve` auf Port 4200 mit `/api`-Proxy.

Der Angular-Router muss also in **drei** Ausführungsumgebungen mit unterschiedlichen Basis-Pfaden
funktionieren, ohne pro Umgebung ein eigenes Build-Artefakt zu erzeugen. Die SPA ist eine
interne Personal-App — SEO, öffentliche Link-Teilbarkeit und Crawler-Indexierung sind kein Ziel.

Zusätzliche Design-Entscheidung, die von der Routing-Wahl abhängt: Die API-Basis-URL wird zur
Laufzeit aus `document.baseURI` als `../api/` abgeleitet (`core/api-base.ts`), damit derselbe
Build in Dev, lokalem JAR und PROD die API korrekt anspricht. Dieser Trick setzt voraus, dass das
Dokument **immer exakt** unter `<context>/app/` liegt.

## Decision

Der Angular-Router verwendet **Hash-basiertes Routing** (`provideRouter(routes,
withHashLocation())` in `app.config.ts`), zusammen mit `baseHref: "./"` in `angular.json`.
SPA-Routen sehen damit so aus: `/w2s/app/#/provider/netflix`, `/app/#/manage` usw.

Serverseitig genügt dafür der minimale `SpaController` mit zwei Mappings:
`GET /app` → Redirect auf `/app/`, `GET /app/` → Forward auf `/app/index.html`. Die statischen
Assets (`/app/*.js`, `/app/*.css`) liefert Spring Boot automatisch.

## Consequences

**Einfacher / besser:**

- **Kein Server-seitiger Deep-Link-Fallback nötig.** Der `#/...`-Teil erreicht den Server nie;
  jeder Deep-Link ist aus Server-Sicht `GET /app/`. Kein Catch-all-Handler, keine Asset-Ausnahmen.
- **Null Kollisionsrisiko** mit den Thymeleaf-Routen — der Server sieht von der SPA nur `/app/`.
- **Ein Build für alle Umgebungen.** `baseHref: "./"` funktioniert in Dev (`ng serve` unter `/`),
  lokalem JAR (`/app/`) und PROD (`/w2s/app/`), ohne `--base-href`-Varianten je Umgebung.
- **Der `document.baseURI`-Trick bleibt trivial korrekt**: `baseURI` ist immer
  `<context>/app/`, `../api/` löst immer auf `<context>/api/` auf — unabhängig von der Routentiefe.
- Robust hinter beliebigen Reverse-Proxies/Context-Paths ohne Rewrite-Regeln.

**Schwieriger / Nachteile:**

- URLs enthalten `#/` und wirken altmodisch bzw. sind „von Hand" schwerer zu lesen.
- Fragment-Anchors (`#abschnitt` zum Scrollen) kollidieren konzeptionell mit dem Routing-Fragment.
- Fragmente werden von manchen externen Systemen schlecht behandelt: **OAuth2/OIDC-Redirect-URIs
  dürfen kein Fragment enthalten**, manche Mail-/Chat-Clients schneiden `#...` beim Verlinken ab.
- Der Server sieht keine SPA-Route → **kein Access-Log/Monitoring pro SPA-Seite**.
- Analytics-Tools bräuchten Extra-Konfiguration für Hash-Change-Tracking. (Für eine interne App
  ohne Analytics irrelevant.)
- SEO: Hash-Routen sind für Crawler zweitklassig. (Für eine interne App bedeutungslos.)

Die Entscheidung ist **später ohne Datenverlust umkehrbar** — URLs sind keine persistierten Daten.
Ein Wechsel auf Path-Routing bedeutet: `withHashLocation()` entfernen, einen Fallback-Controller
ergänzen, `base href` je Umgebung absolut setzen; alte `#/`-Links lassen sich clientseitig per
Redirect weiterleiten. Es entsteht also **kein Lock-in**.

## Alternatives Considered

**Path-Routing (`PathLocationStrategy` / History-API, z. B. `/w2s/app/provider/netflix`).**

Pro: kanonische, „schöne" URLs; Angular-Default; Fragment-Anchors und
`scrollPositionRestoration` ohne Sonderfälle; echte SPA-Pfade in den Server-Logs; SEO-freundlich.

Contra (ausschlaggebend gegen diese Option):

- **Server-Fallback zwingend.** Ein Catch-all `GET /app/**` → forward `index.html` muss
  Asset-Requests (`.js`, `.css`, Fonts) korrekt ausnehmen, sonst wird `index.html` mit `200`
  statt eines `404` für fehlende Bundles ausgeliefert (falsche MIME-Types, stille Fehler) —
  eine klassische Dauer-Fehlerquelle.
- **`baseHref: "./"` bricht.** Bei einem Deep-Link `/w2s/app/provider/netflix` würde ein relatives
  `<base href="./">` auf `/w2s/app/provider/` auflösen → Asset-Requests laufen ins Leere (404 auf
  `main-*.js`). Nötig wäre ein absolutes `base href` (`/app/` lokal, `/w2s/app/` in PROD) →
  entweder **zwei Builds** (`--base-href` je Umgebung) oder serverseitiges Umschreiben der
  `index.html` zur Laufzeit (zusätzliche Komponente).
- Der `../api/`-Trick aus `document.baseURI` funktioniert nur weiter, wenn dieses absolute
  `base href` korrekt gesetzt ist — die Laufzeit-Portabilität des aktuellen Setups (ein Artefakt
  für alle Umgebungen) ginge verloren bzw. müsste durch Index-Rewriting zurückgekauft werden.

Bewertung: Die Hauptargumente für Path-Routing (SEO, Analytics, URL-Ästhetik) sind bei einer
internen Personal-App zweimal irrelevant und einmal Geschmackssache. Die Kosten von Path-Routing
sind dagegen konkret (Zwei-Build-Varianten bzw. Index-Rewrite + fehleranfälliger Fallback) für
null funktionalen Gewinn. Da die Migration jederzeit später möglich ist, fällt die Wahl auf
Hash-Routing.

**Anders entscheiden, wenn:** die App öffentlich wird (SEO/Teilbarkeit zählt), OAuth2/OIDC-Login
mit Redirect in die SPA eingeführt wird (Fragment-URLs sind als Redirect-URI problematisch),
Server-Logs/Monitoring pro SPA-Route gebraucht werden, oder die Thymeleaf-UI komplett von der SPA
abgelöst wird und die SPA auf `/` wandern soll. Dann in einem Zug auf Path-Routing mit absolutem
`base href` und Fallback-Controller umstellen.
