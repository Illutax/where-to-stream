# 0005. AssertJ (mit Mockito) für Backend-Tests

- **Date**: 2026-07-19 (aktualisiert 2026-07-28)
- **Status**: Accepted

## Context

Die Backend-Tests brauchen eine einheitliche Assertion- und Mocking-Bibliothek.
`spring-boot-starter-test` bringt JUnit 5, **AssertJ**, **Mockito** und Hamcrest mit. Im
`pom.xml` ist **Hamcrest bewusst ausgeschlossen** (aus `spring-boot-starter-test`), um nicht zwei
konkurrierende Assertion-Stile im Code zu haben.

**Update 2026-07-28:** `mvn dependency:tree` zeigte einen zweiten, bis dahin unbemerkten Pfad:
`spring-boot-testcontainers` → `org.testcontainers:testcontainers` → `junit:junit:4.13.2` →
`org.hamcrest:hamcrest-core` → `org.hamcrest:hamcrest`. Hamcrest landete also trotz der
Exklusion aus `spring-boot-starter-test` transitiv über Testcontainers wieder auf dem
Test-Klassenpfad. `junit:junit` selbst lässt sich dabei **nicht** entfernen: die
Testcontainers-Kernklassen `GenericContainer`/`JdbcDatabaseContainer` (Basis von
`MariaDBContainer`, siehe `WatchlistEntryRepositoryMariaDbTest` & Co.) implementieren direkt
`org.junit.rules.TestRule` für die alte JUnit4-`@Rule`-API — auch wenn im Projekt ausschließlich
`org.testcontainers:junit-jupiter` (JUnit 5) genutzt wird, muss dieses Interface zur Ladezeit auf
dem Klassenpfad vorhanden sein, sonst wirft die JVM beim Laden dieser Klassen ein
`NoClassDefFoundError: org/junit/rules/TestRule` (live verifiziert). Hamcrest selbst wird dafür
nicht gebraucht — das ist nur ein weiterer transitiver Mitbringsel von `junit:junit` für dessen
eigene `org.junit.Assert`-Matcher-Unterstützung, die hier nie aufgerufen wird.

## Decision

- **Assertions: AssertJ** (`assertThat(...)`), durchgängig. Für Objekte mit mehreren geprüften
  Feldern werden `extracting(...)` / `containsExactly(...)` bzw. Soft-Assertions bevorzugt (siehe
  auch der Team-Skill *consolidate-test-assertions*), damit ein Fehlschlag alle relevanten Werte
  auf einmal zeigt.
- **Test-Doubles: Mockito** (`@Mock`, `@InjectMocks`, `MockitoExtension`; in Web-Slices
  `@MockitoBean`). Feste Uhr in zeitabhängigen Tests via
  `when(timeService.now()).thenReturn(NOW)` (siehe
  [ADR-0003](0003-zeit-ueber-timeservice-facade.md)).
- **Engine: JUnit 5** (Jupiter).
- **Web-Layer**: `MockMvc` mit seinen `status()` / `jsonPath()` / `view()`-`ResultMatchers`;
  Content-Assertions laufen über `andReturn().getResponse().getContentAsString()` + AssertJ (statt
  Hamcrest-`content().string(matcher)`), da Hamcrest nicht auf dem Klassenpfad ist.
- **Hamcrest**: ausgeschlossen — nicht nur aus `spring-boot-starter-test`, sondern auch auf dem
  Testcontainers-Pfad (`spring-boot-testcontainers`, `org.testcontainers:junit-jupiter`,
  `org.testcontainers:mariadb` schließen jeweils `org.hamcrest:hamcrest-core` aus); keine
  `org.hamcrest.*`-Matcher, kein JUnit-`Assertions.assertEquals` für fachliche Prüfungen.
- **JUnit 4** (`junit:junit`): kein Test im Projekt nutzt JUnit4 (`@Test`/`@Rule`/`@RunWith` aus
  `org.junit.*` statt `org.junit.jupiter.*`) — geprüft und bestätigt. Die Bibliothek selbst bleibt
  aber transitiv auf dem Test-Klassenpfad, weil `org.testcontainers:testcontainers`s
  `GenericContainer`/`JdbcDatabaseContainer` das JUnit4-Interface `org.junit.rules.TestRule`
  direkt implementieren; das lässt sich nicht wegkonfigurieren, ohne die MariaDB-Testcontainer
  (`*MariaDbTest`) kaputt zu machen (siehe Context). Das ist eine reine Lademechanik der JVM, kein
  Verstoß gegen diese ADR — im Code selbst ist JUnit4 nirgends erreichbar oder nutzbar.

## Consequences

**Einfacher / besser:**

- Ein einziger, fluenter Assertion-Stil; gute IDE-Autovervollständigung und aussagekräftige
  Fehlermeldungen, besonders bei Collections (`extracting`, `containsExactly`, `hasValueSatisfying`).
- Konsistenz über die gesamte Test-Suite (249 Backend-Tests, Stand 2026-07-28).
- Hamcrest ist jetzt tatsächlich von keinem Pfad mehr erreichbar (`mvn dependency:tree` zeigt keine
  `org.hamcrest:*`-Treffer mehr) statt nur "aus dem direkten Starter ausgeschlossen, aber über einen
  Umweg wieder da".

**Schwieriger / Nachteile:**

- Das Team muss Hamcrest und `Assertions.assertEquals` vermeiden — eine Konvention, die im Review
  auffallen muss.
- Einige `MockMvc`-`ResultMatchers` sind auf Hamcrest-Matcher ausgelegt; wo einer gebraucht würde,
  wird stattdessen auf dem zurückgegebenen Response-Body mit AssertJ geprüft.
- `junit:junit` selbst bleibt (inert) auf dem Test-Klassenpfad, weil Testcontainers es strukturell
  braucht (siehe Context) — jede neue Testcontainers-Version muss stichprobenartig erneut per
  `mvn dependency:tree` geprüft werden, falls sich der Pfad ändert.

## Alternatives Considered

- **Hamcrest**: verbose Matcher-Syntax, überschneidet sich funktional mit AssertJ; bewusst
  ausgeschlossen, um Doppelspurigkeit zu vermeiden.
- **JUnit-eigene `Assertions`**: für einfache Fälle ok, aber deutlich weniger ausdrucksstark bei
  Collections/verschachtelten Objekten.
- **Google Truth**: solide Alternative zu AssertJ, aber AssertJ ist der von Spring Boot
  mitgelieferte Default — kein Grund für eine zusätzliche Abhängigkeit.
