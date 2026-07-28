# 0005. AssertJ (mit Mockito) für Backend-Tests

- **Date**: 2026-07-19 (aktualisiert 2026-07-28)
- **Status**: Accepted

## Context

Die Backend-Tests brauchen eine einheitliche Assertion- und Mocking-Bibliothek.
`spring-boot-starter-test` bringt JUnit 5, **AssertJ**, **Mockito** und Hamcrest mit. Im
`pom.xml` ist **Hamcrest bewusst ausgeschlossen** (aus `spring-boot-starter-test`), um nicht zwei
konkurrierende Assertion-Stile im Code zu haben.

**Update 2026-07-28:** Versuch, JUnit4 und Hamcrest vollständig aus dem `pom.xml` zu entfernen
(inkl. transitiver Abhängigkeiten). Ergebnis: **beides ist strukturell nicht entfernbar**, aus
zwei unabhängigen Gründen — dokumentiert hier, damit niemand das noch einmal versucht.

`mvn dependency:tree` zeigt einen bis dahin unbemerkten Pfad:
`spring-boot-testcontainers` → `org.testcontainers:testcontainers` → `junit:junit:4.13.2` →
`org.hamcrest:hamcrest-core` → `org.hamcrest:hamcrest`. Beide sitzen also trotz der Exklusion
aus `spring-boot-starter-test` transitiv über Testcontainers weiterhin auf dem Test-Klassenpfad.

1. **`junit:junit` lässt sich nicht entfernen:** Die Testcontainers-Kernklassen
   `GenericContainer`/`JdbcDatabaseContainer` (Basis von `MariaDBContainer`, siehe
   `WatchlistEntryRepositoryMariaDbTest` & Co.) implementieren direkt `org.junit.rules.TestRule`
   für die alte JUnit4-`@Rule`-API — auch wenn im Projekt ausschließlich
   `org.testcontainers:junit-jupiter` (JUnit 5) genutzt wird, muss dieses Interface zur Ladezeit
   auf dem Klassenpfad vorhanden sein, sonst wirft die JVM beim Laden dieser Klassen ein
   `NoClassDefFoundError: org/junit/rules/TestRule` (live verifiziert: `mvn -Ptestcontainers
   test` schlägt mit genau diesem Fehler in *jedem* `@SpringBootTest` fehl, sobald `junit:junit`
   ausgeschlossen wird — nicht nur in den MariaDB-Tests selbst, weil der fehlende Typ schon beim
   Klassenladen der Testcontainers-Bibliothek selbst gebraucht wird).

2. **Hamcrest lässt sich — überraschenderweise — ebenfalls nicht entfernen,** obwohl im
   Quellcode nirgends `org.hamcrest.*` importiert oder benutzt wird (geprüft und bestätigt).
   Grund: `org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(...)` und
   `JsonPathResultMatchers.value(...)` — das mit Abstand meistgenutzte Assertion-Idiom in dieser
   Test-Suite (`jsonPath("$.x").value(...)`, dutzendfach in praktisch jedem `@WebMvcTest`) — haben
   in ihrem Overload-Set Signaturen, die `org.hamcrest.Matcher<T>` referenzieren
   (`value(Matcher<? super T>)`, `jsonPath(String, Matcher<? super T>)` usw.), *zusätzlich* zu der
   Hamcrest-freien `value(Object)`-Variante, die wir tatsächlich verwenden. `javac` muss beim
   Kompilieren jedes einzelnen `jsonPath(...).value(...)`-Aufrufs **alle** gleichnamigen Overloads
   auflösen können, um zu entscheiden, welcher zutrifft — dafür muss `org.hamcrest.Matcher` selbst
   ladbar sein, auch wenn am Ende immer die `Object`-Variante gewinnt. Ist Hamcrest komplett weg,
   bricht das mit `error: cannot access Matcher — class file for org.hamcrest.Matcher not found`.
   Live verifiziert per `mvn clean test-compile`: mit `hamcrest-core` aus dem Testcontainers-Pfad
   ausgeschlossen schlägt der Build hart fehl. Tückisch dabei: bei inkrementeller Kompilierung
   (kein `clean`) blieb der Fehler zunächst verborgen (alte `.class`-Dateien wurden wiederverwendet),
   und selbst bei einem vollen Batch-Compile meldete `javac` den Fehler nur für die alphabetisch
   erste betroffene Testklasse (`AdminUserApiControllerTest`) statt für alle — vermutlich eine
   Eigenheit von `javac`s Symbol-Completion-Caching über mehrere Dateien einer Compilation-Unit
   hinweg. Einzeln mit `javac` kompiliert scheitert *jede* Testklasse mit diesem Muster identisch
   (z. B. auch `MeApiControllerTest`) — der Fehler betrifft also praktisch die gesamte
   MockMvc-Test-Suite, nicht nur eine Datei.

**Konsequenz:** Die Exklusionen aus `spring-boot-starter-test` bleiben (schaden nicht, da Hamcrest
ohnehin über Testcontainers hereinkommt), aber es gibt **keine** zusätzliche Exklusion auf dem
Testcontainers-Pfad — der Versuch dazu wurde rückgängig gemacht. `junit:junit` und
`org.hamcrest:*` bleiben beide (ungenutzt, aber strukturell notwendig) auf dem
Test-Klassenpfad.

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
- **Hamcrest**: im Quellcode nicht benutzt — keine `org.hamcrest.*`-Matcher, kein
  JUnit-`Assertions.assertEquals` für fachliche Prüfungen — aber physisch **nicht** vom
  Test-Klassenpfad entfernbar, da Spring Test selbst es für `jsonPath(...).value(...)` braucht
  (siehe Context). Das ist eine reine Javac-Kompilierbarkeitsanforderung, kein Verstoß gegen diese
  ADR — im Code selbst ist Hamcrest nirgends erreichbar oder nutzbar.
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
- Konsistenz über die gesamte Test-Suite (276 Backend-Tests, Stand 2026-07-28).
- Klar dokumentiert und live verifiziert, *warum* JUnit4 und Hamcrest nicht entfernbar sind, statt
  dass das bei einer zukünftigen Dependency-Aufräumaktion erneut mühsam herausgefunden werden muss.

**Schwieriger / Nachteile:**

- Das Team muss Hamcrest und `Assertions.assertEquals` vermeiden — eine Konvention, die im Review
  auffallen muss.
- Einige `MockMvc`-`ResultMatchers` sind auf Hamcrest-Matcher ausgelegt; wo einer gebraucht würde,
  wird stattdessen auf dem zurückgegebenen Response-Body mit AssertJ geprüft.
- `junit:junit` UND `org.hamcrest:*` bleiben (ungenutzt) auf dem Test-Klassenpfad, weil
  Testcontainers bzw. Spring Test selbst sie strukturell brauchen (siehe Context) — jede neue
  Testcontainers- oder Spring-Boot-Version muss stichprobenartig erneut per `mvn dependency:tree`
  bzw. `mvn clean test-compile` geprüft werden, falls sich einer der beiden Pfade ändert.

## Alternatives Considered

- **Hamcrest**: verbose Matcher-Syntax, überschneidet sich funktional mit AssertJ; bewusst
  ausgeschlossen, um Doppelspurigkeit zu vermeiden.
- **JUnit-eigene `Assertions`**: für einfache Fälle ok, aber deutlich weniger ausdrucksstark bei
  Collections/verschachtelten Objekten.
- **Google Truth**: solide Alternative zu AssertJ, aber AssertJ ist der von Spring Boot
  mitgelieferte Default — kein Grund für eine zusätzliche Abhängigkeit.
