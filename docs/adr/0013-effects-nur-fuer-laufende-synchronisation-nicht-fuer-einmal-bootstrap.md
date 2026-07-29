# 0013. `effect()` nur für laufende Synchronisation, nicht für einmaliges Bootstrapping

- **Date**: 2026-07-29
- **Status**: Accepted

## Context

`app.ts` hat die Nutzer-Preferences bisher so aus dem geladenen Principal übernommen:

```ts
effect(() => {
  const me = this.auth.me();
  if (me) {
    this.userPrefsStore.init(me);
  }
});
```

Das sieht nach normaler Signal-Reaktivität aus, ist aber semantisch etwas anderes: `auth.me()`
geht genau einmal pro App-Lebenszeit von `null` auf einen Wert über (kein Re-Login ohne Reload),
d. h. der Effect soll **einmalig** reagieren, wenn die asynchron geladenen Daten eintreffen — nicht
fortlaufend auf Änderungen synchronisieren.

Dieser Kategorie-Fehler hat einen echten Bug ermöglicht: `UserPrefsStore.init()` las intern sein
eigenes `_prefs`-Signal (für `applyTheme`). Dieser Read registrierte sich als Dependency des
aufrufenden Effects — mit der Konsequenz, dass *jede* spätere `setViewMode()`/`setTilesPerRow()`/…
(die `_prefs` schreiben) den Effect erneut auslöste, der `init(me)` mit dem ursprünglichen,
inzwischen veralteten `me`-Snapshot erneut aufrief und die gerade gemachte Änderung augenblicklich
zurücksetzte. Behoben wurde das kurzfristig mit `untracked(() => …)` in `init()` — korrekt und
bewusst beibehalten (schützt jeden künftigen Aufrufer von `init()`), aber das beseitigt nur das
Symptom an dieser einen Stelle. Die eigentliche Ursache — ein `effect()`, der strukturell *jede*
neue, versehentlich eingeschleppte Dependency automatisch mitnimmt, obwohl fachlich nur eine
einmalige Aktion gemeint war — bleibt für den nächsten ähnlichen Fall bestehen (jeder künftige
"lade X einmalig, sobald Y verfügbar ist"-Effect kann denselben Fehler wiederholen).

## Decision

**`effect()` ist reserviert für fortlaufende, tatsächlich wiederholbare Synchronisation** ("immer
wenn Signal X sich ändert, tu Y") — z. B. die UI-Sprache an `userPrefsStore.language()` zu koppeln
(`app.ts`) oder Titel-Metadaten nachzuladen, sobald sich Preference- oder Id-Signale ändern
(`core/title-meta.ts`).

**Einmalige, durch das Eintreffen asynchroner Daten ausgelöste Aktionen** ("sobald X einmal
verfügbar ist, tu Y — danach nie wieder automatisch") werden **nicht** über `effect()` auf ein
Signal moduliert, sondern direkt an die auslösende Quelle gehängt — regulär per RxJS-Subscription
auf das Observable, das die Daten liefert. Konkret: `AuthStore.load()` gibt jetzt das (via
`shareReplay(1)` geteilte) `Observable<Me | null>` zurück; `app.ts` abonniert es einmal und ruft
`userPrefsStore.init(me)` direkt im Callback auf — kein Signal-Read-Tracking involviert, die ganze
Bug-Klasse ("Read wird versehentlich zur Effect-Dependency") ist an dieser Stelle strukturell
ausgeschlossen, nicht nur durch Disziplin (`untracked`) vermieden.

Faustregel für neuen Code: Würde die Aktion bei einer erneuten Signal-Änderung *nochmal* sinnvoll
ausgeführt werden wollen? Wenn ja: `effect()`. Wenn nein (sie soll nur beim allerersten
Eintreffen der Daten laufen): direkte Subscription auf die Quelle, kein `effect()`.

## Consequences

**Einfacher / besser:**

- Die Bug-Klasse aus dem Kontext ist strukturell ausgeschlossen, nicht nur durch eine
  `untracked()`-Konvention entschärft, die man an jeder neuen Stelle erneut befolgen müsste.
  `untracked()` in `UserPrefsStore.init()` bleibt zusätzlich als zweite Verteidigungslinie
  bestehen (schützt auch Aufrufer außerhalb dieses einen Bootstrap-Pfads).
- Der Code liest sich näher an der fachlichen Absicht: "wenn `/api/me` einmal geladen hat, tu X"
  statt "beobachte dieses Signal fortlaufend, aber eigentlich nur für den ersten Wert."

**Schwieriger / Nachteile:**

- `AuthStore.load()` ist nicht mehr komplett "fire and forget" (`void`-Rückgabe) — es gibt jetzt
  einen Rückgabewert, den nicht jeder Aufrufer braucht. Rein optionale API-Erweiterung, bestehende
  Aufrufer, die den Rückgabewert ignorieren, sind unverändert lauffähig.
- Ein zweiter Stil neben Signal-Effects im selben Codebereich ("mal Observable-Subscription, mal
  Effect") — Faustregel oben soll die Entscheidung an jeder neuen Stelle eindeutig machen.

## Alternatives Considered

- **Nur `untracked()` behalten, Architektur unangetastet lassen**: schnell, aber lässt das
  strukturelle Risiko (nächster ähnlicher Effect an anderer Stelle) unadressiert — genau der Punkt,
  der zu dieser ADR geführt hat.
- **Effect beibehalten, aber Kommentar/Konvention "keine ungetrackten Reads in diesem Effect"
  hinzufügen**: rein dokumentarisch, keine strukturelle Absicherung; genauso leicht zu vergessen
  wie die ursprüngliche `untracked()`-Lücke selbst.
- **`toSignal()` des Auth-Ladevorgangs plus `effect()` mit `{ once: true }`-artigem Muster**:
  Angular kennt kein natives `{ once: true }` für Effects; müsste selbst gebaut werden (Flag +
  manuelles Abbrechen) — mehr Code für denselben Effekt wie eine simple RxJS-Subscription.
