# 0002. Frontend-Build über exec-maven-plugin (System-Node) statt frontend-maven-plugin

- **Date**: 2026-07-19
- **Status**: Accepted

## Context

Der Angular-Client (`src/main/frontend`) muss Teil des **einen** Fat-JARs bleiben: `mvn package`
baut das Frontend und kopiert `dist/w2s-ui/browser` nach `static/app` auf den Classpath. Dafür
muss der Maven-Build während `package` einen Node/npm-Toolchain aufrufen (`npm ci` +
`npm run build`).

Rahmenbedingungen der Build-Umgebung:

- Dev-Container: Node v24.18.0, npm 11.16.0 vorhanden; `registry.npmjs.org` über einen
  Nexus-Mirror erreichbar. `mvn deploy` ist blockiert, `mvn package` funktioniert.
- Docker-Builder-Stage basiert auf `maven:3-amazoncorretto-25-alpine` (musl/Alpine).
- Die Erreichbarkeit von `nodejs.org` (für einen Node-Self-Download) ist im Sandbox-/Proxy-Setup
  **nicht verifiziert**.
- `node_modules` ist ~300 MB groß; `npm ci` löscht und installiert es bei jedem Aufruf neu.

Zwei etablierte Wege, npm aus Maven aufzurufen: `exec-maven-plugin` (ruft ein
**vorinstalliertes** System-npm) oder `com.github.eirslett:frontend-maven-plugin` (lädt eine im
`pom.xml` **gepinnte** Node-Version selbst von `nodejs.org` herunter).

## Decision

Der Frontend-Build läuft über **`exec-maven-plugin`** mit dem System-`npm`, gebunden an die Phase
`generate-resources` (`npm run build`), plus einer separaten, geführten `npm ci`-Ausführung.
`-Dskip.frontend=true` überspringt den gesamten Frontend-Build (Backend-only).

Zusätzlich sind drei Härtungen umgesetzt (siehe auch Consequences):

1. **Node-Version-Pinning ohne Plugin**: `engines` (`node: ">=22 <25"`) in `package.json`,
   `engine-strict=true` in `src/main/frontend/.npmrc`, `.nvmrc` für Dev-Laptops; der
   Docker-Builder kopiert ein festes Node aus `node:24-alpine` statt eines ungepinnten
   `apk add nodejs`.
2. **`npm ci` nur bei geändertem Lock-File**: Ein `maven-antrun-plugin`-`uptodate`-Check
   vergleicht `node_modules/.package-lock.json` (von npm nach jeder Installation geschrieben) mit
   `package-lock.json`; ein Ant-`unless:set`-Guard führt `npm ci` nur aus, wenn die Deps stale
   sind. Das spart bei unverändertem Lock-File die ~300-MB-Neuinstallation.
3. **Windows-Kompatibilität**: Ein OS-aktiviertes Maven-Profil `windows` setzt
   `npm.executable=npm.cmd`; der Build referenziert `${npm.executable}`.

## Consequences

**Einfacher / besser:**

- **Funktioniert nachweislich in allen drei Umgebungen** (Dev-Container, Docker-Builder, lokale
  Builds) ohne Abhängigkeit von `nodejs.org` — dessen Erreichbarkeit ist hier unverifiziert.
- Simpel und transparent: `exec`-Aufruf von `npm`; `exec-maven-plugin` ist ein Standard-Plugin
  mit trivialer Wartung, das Node selbst nicht kennt (keine Plugin-Updates bei neuen Node-Majors).
- Nutzt den vorhandenen npm-Setup inkl. dessen Registry-Konfiguration — passt zur
  Nexus-Mirror-Policy des Projekts.
- Durch Härtung 1 ist die Node-Version über Maschinen hinweg kontrolliert; ein falsches Node
  bricht den Install dank `engine-strict` mit klarer Meldung statt subtiler Fehler ab.
- Durch Härtung 2 kostet ein wiederholter `mvn package` bei unverändertem Lock-File keine
  300-MB-Neuinstallation mehr.
- `-Dskip.frontend=true` bietet einen sauberen Backend-only-Pfad.

**Schwieriger / Nachteile:**

- **„Node muss vorinstalliert sein"** bleibt eine implizite Voraussetzung: Ein frischer Rechner
  ganz ohne Node bricht mit `Cannot run program "npm"` ab (nicht mit einer fachlichen Meldung).
- Der Docker-Builder pinnt Node über einen zusätzlichen Multi-Stage-Copy (`node:24-alpine`) —
  minimal mehr Dockerfile-Komplexität als ein `apk add`.
- Reproduzierbarkeit hängt weiterhin teils an Disziplin (Pinning per `engines`/`.nvmrc`), nicht an
  einem Tool, das die Toolchain erzwingt und mitliefert.
- Die `npm ci`-Guard-Logik (antrun `uptodate` + `unless:set`) ist Build-Sonderlogik, die man
  kennen muss; ihre Korrektheit wurde in beide Richtungen verifiziert (skippt bei aktuellem
  Lock-File, läuft bei getouchtem Lock-File).

## Alternatives Considered

**`com.github.eirslett:frontend-maven-plugin`** (lädt eine gepinnte Node-Version selbst).

Pro: gepinnte Node/npm-Version direkt im `pom.xml` → identische Toolchain auf CI, Docker-Builder
und jedem Dev-Laptop, per `mvn package` ohne jede Vorinstallation; Windows out of the box;
air-gapped-tauglich, **sobald** ein Nexus-Raw-Proxy für `nodejs.org/dist` steht.

Contra (ausschlaggebend gegen diese Option):

- **Self-Download von `nodejs.org` ist der Default — und genau dessen Erreichbarkeit ist im
  Sandbox-/Proxy-Setup unverifiziert.** Ohne funktionierenden Download bricht jeder Build hart ab.
  Abhilfe (`nodeDownloadRoot` auf ein Nexus-Raw-Repo) erfordert eine **neue, heute nicht
  existierende** Nexus-Repo-Konfiguration (Raw-Format).
- Im Docker-Builder doppelte Node-Installation bzw. ein nicht layer-cachebarer Download innerhalb
  der `mvn package`-Layer.
- Das Plugin ist eher im Maintenance-Modus (träge Release-Zyklen; neue Node-Majors brauchen
  gelegentlich Plugin-Updates).
- Löst das `npm ci`-bei-jedem-Build-Problem **nicht** — es führt dieselben npm-Goals aus.

Bewertung: Der einzige substanzielle Vorteil (gepinnte Node-Version) hängt in dieser Umgebung an
einer unverifizierten Voraussetzung; ein Wechsel tauschte ein theoretisches
Reproduzierbarkeitsproblem gegen ein reales Build-Bricht-Risiko. Die Lücken der gewählten Lösung
lassen sich billiger schließen (die drei Härtungen oben), daher bleibt es bei `exec-maven-plugin`.

**Anders entscheiden, wenn:** die Nexus-Instanz ein Raw-Proxy-Repo für `nodejs.org/dist` bekommt
(dann kippt die Abwägung: gepinnte Node-Version + vollständige Nexus-Abdeckung bei null
Internet-Abhängigkeit), das Team wächst und heterogene Dev-Maschinen (insb. Windows ohne
vorinstalliertes Node) dazukommen, ein konkreter Bug durch Node-Version-Drift auftritt, oder
CI-Runner ohne vorinstalliertes Node eingeführt werden. In diesen Fällen ist
`frontend-maven-plugin` mit `nodeDownloadRoot` auf Nexus die richtige Wahl — die Migration ist
dann ein überschaubarer pom-Umbau ohne Änderung am Frontend selbst.
