---
name: ticket
description: Legt ein TODO in TODOs.md an, arbeitet eines ab oder schließt es nach DONE.md. Use when opening, updating or closing a TODO/ticket in this repository, or when unsure whether something belongs in TODOs.md, an ADR, or a commit message.
---

# Ticketarbeit

Zwei Dateien, zwei Aufgaben:

| Datei | Enthält | Wird gepflegt | Wird geprüft |
| --- | --- | --- | --- |
| `TODOs.md` | **nur offene Arbeit** | ja — was hier steht, gilt jetzt | `DocumentationConsistencyTest` |
| `DONE.md` | erledigt und verworfen | **nein**, ausdrücklich | nein |

Die Trennung ist der ganze Punkt. Vor dem 2026-09-09 lag beides zusammen, 86 % der Zeilen
waren Historie im Präsens, und niemand konnte einem Eintrag ansehen, ob ein alter Klassenname
bloß alt oder die Aussage falsch geworden war. Wer erledigte Einträge in `TODOs.md` abhakt
statt sie zu verschieben, stellt genau diesen Zustand wieder her.

## Ein Ticket anlegen

Erst die Vorfrage: **gehört es überhaupt hierher?**

| Was | Wohin |
| --- | --- |
| Etwas ist zu tun | `TODOs.md` |
| Eine Entscheidung mit Begründung, die dauerhaft gilt | ADR (`adr`-Skill) |
| Warum diese eine Änderung so aussieht | Commit-Nachricht |
| Wie man das Projekt baut und betreibt | `README.md` |
| Wie wir zusammenarbeiten | `CLAUDE.md` |

Ein Ticket, das nur eine Erkenntnis festhält, ohne dass jemand etwas tun soll, ist an dieser
Stelle falsch — es wird nie abgearbeitet und altert vor sich hin.

Dann die nächste freie Nummer aus `TODOs.md` **und** `DONE.md` (die höchste von beiden + 1) und:

```markdown
### 🟠 TODO-N — Kurzer Titel in der Sache, nicht in der Lösung
Was ist der Fall, und warum zählt es. Belege mit Pfad.

- **Akzeptanzkriterium:** Woran man erkennt, dass es fertig ist.
```

Dazu die Zeile in die Übersichtstabelle am Kopf der Datei, in der Reihenfolge
🔴 → 🟠 → 🟡 → 🟢, innerhalb einer Stufe nach Nummer.

### Die vier Regeln, und warum es sie gibt

1. **Zeigen statt wiederholen.** Verlinke die maßgebliche Stelle, statt ihren Inhalt
   abzuschreiben. `ddl-auto` stand einmal an drei Stellen — zwei davon behaupteten `validate`,
   lange nachdem es `none` war. Jede Kopie einer Tatsache driftet für sich.
2. **Belege mit Pfad.** `src/main/java/.../ExportReader.java` statt „irgendwo im Service".
   Der Test prüft beim Build, dass es die Datei gibt: ein Pfad in Backticks, den es nicht gibt,
   macht rot. Das ist kein Formalismus — genau diese Drift war die häufigste von allen.
3. **Ungeprüftes als ungeprüft kennzeichnen.** „Nicht verifiziert:" ist eine vollwertige
   Aussage. Eine Vermutung, die wie ein Befund aussieht, kostet später mehr als sie spart:
   TODO-61 trug einen Tag lang eine plausible Ursache, die sich als falsch erwies, und
   TODO-14 stand Monate offen wegen einer Prämisse, die nie stimmte.
4. **Kein ✅ in `TODOs.md`.** Erledigtes wird verschoben, nicht abgehakt. Der Test erzwingt das.

## Ein Ticket abarbeiten

- **Erst prüfen, ob die Beschreibung noch stimmt.** Tickets altern. Steht dort eine Prämisse,
  die sich nicht mehr halten lässt, ist das der eigentliche Befund — dann wird das Ticket
  korrigiert oder verworfen, nicht blind umgesetzt.
- Änderungen wie üblich: Test dazu, `mvn verify` und `ng test` grün.
- Fällt unterwegs etwas Neues auf, das nicht zum Ticket gehört: eigenes Ticket, nicht anhängen.

## Ein Ticket schließen

1. **Dauerhaft wertvolle Begründung sichern**, *bevor* verschoben wird — als ADR, wenn sie
   künftige Entscheidungen bindet. `DONE.md` wird nicht gepflegt; was dort landet, ist ab
   dann Geschichte und wird nicht mehr gelesen, wenn jemand den Ist-Zustand wissen will.
2. Statusmarker auf ✅ (erledigt) oder ❌ (verworfen) setzen und einen Absatz ergänzen:
   **was tatsächlich gemacht wurde**, und was daran vom ursprünglichen Plan abwich.
   Bei ❌ vor allem: warum verworfen — das hält den Nächsten davon ab, es erneut anzufangen.
3. Eintrag nach `DONE.md` verschieben, Zeile aus der Übersichtstabelle entfernen.
4. `mvn verify` — der Test fängt Querverweise ab, die jetzt ins Leere zeigen.

Verweise **auf** ein geschlossenes Ticket bleiben gültig: der Test sucht in beiden Dateien.

## Was der Test nicht kann

Er prüft Pfade, Links, Nummern und Marker. Ob eine **Aussage** noch stimmt, sieht er nicht —
ein Eintrag darf grün sein und trotzdem Unsinn behaupten. Der Test verkleinert die Menge
dessen, was ein Mensch nachsehen muss; er ersetzt das Nachsehen nicht.
