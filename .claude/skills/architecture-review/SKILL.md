---
name: architecture-review
description: Führt ein Architecture Review des w2s-Backends und -Frontends durch und schreibt das Ergebnis als datierte Momentaufnahme nach docs/reviews/. Use when the user asks for an architecture review, an Ist-Zustand of the architecture, or wants to check whether the ADRs still describe reality.
---

# Architecture Review

Erzeugt eine **datierte Momentaufnahme** des Architekturzustands unter
`docs/reviews/JJJJ-MM-TT-architecture-review.md`.

## Warum die Form so streng ist

Der Vorgänger (`docs/reviews/2026-07-28-architecture-review.md`) stand undatiert unter `docs/`
und wurde deshalb gelesen, als beschriebe er den heutigen Stand. Einen Tag nach seiner
Entstehung entschied ADR-0014 den Umbau, den er selbst ausgelöst hatte — und das Dokument
beschrieb ab da eine Welt, die es nicht mehr gab. Aufgefallen ist das erst Monate später.

Daraus die zwei Regeln, von denen nicht abgewichen wird:

1. **Datum im Dateinamen, und nach dem Schreiben wird nicht mehr geändert** (Tippfehler
   ausgenommen). Ein Stand darf altern. Ein „so ist die Architektur" darf es nicht.
2. **Das Reviewdokument ist kein Backlog.** Was dauerhaft gilt, wird eine ADR; was zu tun ist,
   wird ein TODO. Im Review steht nur der Befund und seine Begründung. Ohne diese Regel
   entsteht ein drittes Dokument, das mit `TODOs.md` und `docs/adr/` auseinanderläuft — und
   genau diese Art Drift ist das, was das Review eigentlich finden soll.

## Vorher prüfen

- Sind `TODO-62` (README-Abgleich) und `TODO-64` (Aufräumfunde) erledigt? Wenn nicht: nachfragen.
  Ein Review auf bekannt falscher Doku produziert Befunde, die schon als Ticket existieren.
- `mvn verify` und `ng test` grün? Ein Review auf rotem Baum vermischt Symptome.

## Durchführung

Der Umfang ist zu groß für einen Durchgang. Aufteilen und **parallel** von Subagenten prüfen
lassen — je Agent ein abgeschlossener Bereich, damit er wirklich lesen kann statt zu greppen:

| Bereich | Kernfrage |
| --- | --- |
| Kontextgrenzen | Halten die vier Bounded Contexts? Wer greift woran vorbei? Deckt `ArchitectureTest` das ab? |
| `shared` | Ist `kernel`/`platform` noch die richtige Trennung, oder ist `shared` zur Resterampe geworden? |
| Persistenz | Entities, Repositories, Liquibase-Changelog, Indizes — passt das Modell noch zur Nutzung? |
| Outbound-Adapter | werstreamt.es, IMDb, TMDB: Rate-Limits, Fehlerbehandlung, Timeouts, Ausfallverhalten |
| Frontend | Signals/Stores, Ladezustände, geteilte Komponenten, Bundle-Aufbau |
| **ADR-Abgleich** | **Welche der ADRs beschreibt die Realität nicht mehr?** Je ADR: gilt sie, ist sie überholt, oder wurde sie stillschweigend gebrochen? |

Die letzte Zeile ist die wertvollste und wird gern vergessen. Eine ADR, die niemand mehr
befolgt, ist schlimmer als keine — sie sieht aus wie eine Zusicherung.

Jedem Agenten mitgeben:
- Er **ändert nichts**, er berichtet.
- Belege mit `datei:zeile`, keine Vermutungen als Befund.
- Was er **nicht** prüfen konnte, sagt er.

## Ergebnis schreiben

`docs/reviews/JJJJ-MM-TT-architecture-review.md` mit:

- **Kopf:** Datum, Umfang, was ausdrücklich *nicht* geprüft wurde, Commit-Stand (`git rev-parse --short HEAD`).
- **Je Befund:** was, wo (`datei:zeile`), warum es zählt — und ob es eine ADR bestätigt,
  ihr widerspricht oder eine Lücke zeigt.
- **Kein Maßnahmenteil.** Stattdessen je Befund ein Verweis auf das TODO oder die ADR,
  die daraus entstanden ist. Diese Verweise werden erst eingetragen, nachdem die Tickets
  angelegt sind — sonst zeigt das Dokument ins Leere.

Danach:
1. TODOs in `TODOs.md` anlegen (Format und Regeln stehen dort im Kopf).
2. ADRs über den `adr`-Skill; überholte ADRs auf `Superseded` setzen, den Index nachziehen.
3. `mvn verify` — `DocumentationConsistencyTest` fängt tote Pfade und Querverweise ab.

## Turnus

Kein fester. Sinnvolle Auslöser: ein abgeschlossener größerer Umbau, ein Kontext, der neu
dazukommt, oder der Eindruck, dass die ADRs die Realität nicht mehr treffen. Ein Review
„weil ein Quartal um ist" produziert Prosa; ein Review nach einem Umbau produziert Befunde.
