# 0005. AssertJ (mit Mockito) für Backend-Tests

- **Date**: 2026-07-19
- **Status**: Accepted

## Context

Die Backend-Tests brauchen eine einheitliche Assertion- und Mocking-Bibliothek.
`spring-boot-starter-test` bringt JUnit 5, **AssertJ**, **Mockito** und Hamcrest mit. Im
`pom.xml` ist **Hamcrest bewusst ausgeschlossen** (aus `spring-boot-starter-test`), um nicht zwei
konkurrierende Assertion-Stile im Code zu haben.

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
- **Hamcrest**: ausgeschlossen; keine `org.hamcrest.*`-Matcher, kein JUnit-`Assertions.assertEquals`
  für fachliche Prüfungen.

## Consequences

**Einfacher / besser:**

- Ein einziger, fluenter Assertion-Stil; gute IDE-Autovervollständigung und aussagekräftige
  Fehlermeldungen, besonders bei Collections (`extracting`, `containsExactly`, `hasValueSatisfying`).
- Konsistenz über die gesamte Test-Suite (114 Backend-Tests).

**Schwieriger / Nachteile:**

- Das Team muss Hamcrest und `Assertions.assertEquals` vermeiden — eine Konvention, die im Review
  auffallen muss.
- Einige `MockMvc`-`ResultMatchers` sind auf Hamcrest-Matcher ausgelegt; wo einer gebraucht würde,
  wird stattdessen auf dem zurückgegebenen Response-Body mit AssertJ geprüft.

## Alternatives Considered

- **Hamcrest**: verbose Matcher-Syntax, überschneidet sich funktional mit AssertJ; bewusst
  ausgeschlossen, um Doppelspurigkeit zu vermeiden.
- **JUnit-eigene `Assertions`**: für einfache Fälle ok, aber deutlich weniger ausdrucksstark bei
  Collections/verschachtelten Objekten.
- **Google Truth**: solide Alternative zu AssertJ, aber AssertJ ist der von Spring Boot
  mitgelieferte Default — kein Grund für eine zusätzliche Abhängigkeit.
