# 0015. Selbstvalidierende Commands statt verstreuter Request-Validierung

- **Date**: 2026-07-30
- **Status**: Accepted

## Context

Zwei Muster für "Eingabe in einen Use-Case" standen nebeneinander, ohne dass klar war, welches
das Vorbild ist:

- **Unpacked Primitives**: die meisten Services nahmen einzelne, aus dem Request entpackte Werte
  entgegen — `WatchlistImportService.addOne(UUID userId, ImdbId imdbId, String name, ReleaseYear
  year)`, `UserPreferencesService.updateTheme(String username, Theme theme)`.
- **Whole-Request-Object**: `UserAdminService.create(CreateUserRequest request)` nahm dagegen das
  komplette, vom Controller ungeöffnete Request-Objekt entgegen.

Keines der beiden validierte sich selbst.
Validierung war reine Controller-Prosa — derselbe Block
(`if (request == null || request.feld() == null) throw new ValidationException(...)`) stand an
neun Stellen fast wortgleich, ohne Bean Validation (`@Valid`/`@NotNull`), die im ganzen Projekt
nirgends verwendet wird.

Bei `UserAdminService` war die Grenze zwischen "strukturelle Validierung" (leerer Username/
Passwort) und "fachliche Regel" (doppelter Username, letzter Admin) zusätzlich verwischt: beide
warfen `UserManagementException`, obwohl nur Letzteres eine echte Geschäftsregel ist (braucht
DB-Zugriff) — Ersteres ist eine reine Formprüfung, die keinen Repository-Zugriff braucht.

## Decision

**Wire-`*Request`-Records bleiben dumme Datenträger** (`adapter/in/api`, per `@RequestBody`
gebunden, keine eigene Validierung) — das ist unverändert.
**Neue `*Command`-Records** (`application/command/`) sind das, was jede Anwendungsservice-Methode
jetzt entgegennimmt: **genau ein Parameter pro Methode**, der sowohl den Kontext (Username, UserId,
ImdbId — aus `Authentication`/`@PathVariable`) als auch die Nutzdaten aus dem Wire-Request bündelt,
und der sich **im eigenen Compact-Constructor selbst validiert** — genau das Muster, das `ImdbId`
schon für sein `tt\w+`-Format nutzt, nur hier für Pflichtfelder/Wertebereiche statt Formate.

```java
public record ThemeUpdateCommand(String username, Theme theme) {
    public ThemeUpdateCommand {
        if (theme == null) {
            throw new ValidationException("A theme is required.");
        }
    }
}
```

Der Controller konstruiert das Command aus Wire-Request plus Kontext und reicht **nur noch das
Command** an den Service weiter — keine losen Parameter mehr, keine manuelle Prüfung mehr im
Controller-Methodenkörper:

```java
public void updateTheme(Authentication authentication, @RequestBody ThemeUpdateRequest request) {
    userPreferencesService.updateTheme(new ThemeUpdateCommand(authentication.getName(), request.theme()));
}
```

**Wo Wire-Request und Command identisch wären** (keine zusätzlichen Kontext-Felder nötig, z. B.
`CreateUserCommand`, `InvalidateCommand`), entfällt der separate Wire-Typ — das Command selbst ist
dann direkt per `@RequestBody` gebunden.

**Strukturelle Validierung wandert in den Compact-Constructor, fachliche Regeln bleiben im
Service.** Bei `UserAdminService` bedeutete das eine echte Klärung, nicht nur eine Verschiebung:
leerer Username/Passwort wirft jetzt `ValidationException` (aus `CreateUserCommand`s eigenem
Constructor, keine DB-Interaktion nötig), während doppelter Username und "letzter Admin" weiterhin
`UserManagementException` im Service werfen (beide brauchen einen Repository-Zugriff, den ein
Record-Constructor nicht machen darf). Ebenso wanderte `UsernameUpdateCommand`s
Verfügbarkeits-Prüfung (vorher im Controller: `usernameAvailable()` aufrufen, dann erst
`updateUsername()`) vollständig in `UserPreferencesService.updateUsername` selbst — der Service
besitzt jetzt seine eigene Geschäftsregel komplett, statt sie sich vom Controller vorprüfen zu
lassen.

**Verifiziert, nicht angenommen**: bevor Command-Validierung testweise direkt in einen
`@RequestBody`-gebundenen Typ verlegt wurde (die Variante ohne separaten Wire-Typ), wurde geprüft,
ob eine im Compact-Constructor geworfene `ValidationException` beim Aufruf durch Jacksons
Record-Deserialisierung überhaupt noch bei `ApiExceptionHandler` ankommt.
Sie tut es: Spring löst die Ursachenkette von `HttpMessageNotReadableException` auf und findet den
passenden `@ExceptionHandler` — Status, `application/problem+json`-Content-Type und die exakte
`detail`/`title`-Nachricht bleiben identisch zur vorherigen, im Controller geworfenen Variante.

## Consequences

**Einfacher / besser:**

- Jede Service-Methode hat jetzt exakt eine Signatur-Form (`methode(XyzCommand command)`) statt
  zwei konkurrierender Stile.
- Validierung steht an **einer** Stelle pro Feld (im Command), nicht an einer Controller-Zeile,
  die bei jedem neuen Endpunkt neu abgetippt wird.
- `UserAdminService` verlor seine `requireText`/`roles`-Hilfsmethoden vollständig — beide Prüfungen
  leben jetzt direkt in `CreateUserCommand`/`UpdateUserCommand`.
- `WatchlistEntryRepository`-artige Wrapper-Logik (zwei Zeilen "null → leere Liste") existierte für
  `InvalidateRequest` doppelt (Controller **und** Service) — jetzt einmal, in
  `InvalidateCommand`s Compact-Constructor.

**Schwieriger / Nachteile:**

- Mehr Dateien: sieben Preference-Commands brauchen weiterhin ihre dummen Wire-Requests daneben
  (Jackson kann `username`/`userId` nicht aus dem JSON-Body lesen), macht 14 statt 7 Typen für die
  `/api/me/*`-Endpunkte.
- Records können keine bedingte Typkonvertierung im Compact-Constructor vornehmen (z. B. `Integer`
  rein, `ReleaseYear` raus) ohne einen zusätzlichen, überladenen Constructor — bewusst vermieden
  (siehe Alternativen), Commands tragen deshalb teils noch rohe Wire-Typen (`Integer year` statt
  `ReleaseYear year`), Services konvertieren den letzten Schritt selbst.

## Alternatives Considered

- **Volles CQRS** (getrennte Read-/Write-Modelle, ggf. Event Sourcing): für dieses Projekt klar
  überdimensioniert — eine einzelne DB, kein Skalierungsdruck, keine fachliche Notwendigkeit für
  Event-Historie. Verworfen ohne nähere Prüfung.
- **`*Dto` in Richtung "Query-Result" umbenennen**: DTOs sind schon reine Ausgabe-Projektionen,
  nie für Eingabe wiederverwendet — rein kosmetische Umbenennung ohne strukturellen Gewinn.
  Verworfen.
- **Jedes Command bekommt zusätzlich einen überladenen Constructor für Wire-Typ-Konvertierung**
  (z. B. `CreateUserCommand(String, String, String, List<Role>)` plus eine Canonical-Variante mit
  `Set<Role>`): probiert für `CreateUserCommand`, dann verworfen — eine zusätzliche
  Constructor-Überladung pro Command nur für eine einzige Typkonvertierung ist mehr Zeremonie, als
  sie einspart; die Konvertierung bleibt eine Zeile im Service.
- **Command auch dort einführen, wo es keinen Bedarf gibt** (z. B. `ClearCommand(UUID userId)` für
  `WatchlistApiController.clear`): verworfen — ein Wrapper um einen einzelnen, immer vertrauten
  Wert ohne Validierungsmöglichkeit wäre reine Zeremonie, dieselbe Begründung wie das Fehlen eines
  Ports für `ImdbTitleClient`/`ImdbSuggestionClient` in ADR-0014.
