# 0005. AssertJ (with Mockito) for backend tests

- **Date**: 2026-07-19 (updated 2026-07-28)
- **Status**: Accepted

## Context

The backend tests need one common assertion and mocking library.
`spring-boot-starter-test` brings JUnit 5, **AssertJ**, **Mockito** and Hamcrest with it.
In `pom.xml`, **Hamcrest is deliberately excluded** (from `spring-boot-starter-test`), so that the
code does not end up with two competing assertion styles.

**Update 2026-07-28:** An attempt to remove JUnit4 and Hamcrest from `pom.xml` entirely
(including transitive dependencies).
Result: **neither can be removed structurally**, for
two independent reasons — documented here so that nobody tries it a second time.

`mvn dependency:tree` reveals a path that had gone unnoticed until then:
`spring-boot-testcontainers` → `org.testcontainers:testcontainers` → `junit:junit:4.13.2` →
`org.hamcrest:hamcrest-core` → `org.hamcrest:hamcrest`.
So despite the exclusion from
`spring-boot-starter-test`, both still sit on the test classpath transitively via Testcontainers.

1. **`junit:junit` cannot be removed:** the Testcontainers core classes
   `GenericContainer`/`JdbcDatabaseContainer` (the basis of `MariaDBContainer`, see
   `WatchlistEntryRepositoryMariaDbTest` and friends) directly implement `org.junit.rules.TestRule`
   for the old JUnit4 `@Rule` API — even though the project uses exclusively
   `org.testcontainers:junit-jupiter` (JUnit 5), that interface has to be present on the classpath
   at load time, or the JVM throws a
   `NoClassDefFoundError: org/junit/rules/TestRule` while loading those classes (verified live:
   `mvn -Ptestcontainers test` fails with exactly that error in *every* `@SpringBootTest`
   as soon as `junit:junit` is excluded — not just in the MariaDB tests themselves, because the
   missing type is already needed when the Testcontainers library's own classes are loaded).
2. **Hamcrest — surprisingly — cannot be removed either,** even though nothing in the
   source code imports or uses `org.hamcrest.*` (checked and confirmed).
   The reason: `org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(...)` and
   `JsonPathResultMatchers.value(...)` — by far the most frequently used assertion idiom in this
   test suite (`jsonPath("$.x").value(...)`, dozens of times in practically every `@WebMvcTest`) —
   have signatures in their overload set that reference `org.hamcrest.Matcher<T>`
   (`value(Matcher<? super T>)`, `jsonPath(String, Matcher<? super T>)` and so on), *in addition* to
   the Hamcrest-free `value(Object)` variant that we actually use.
   When compiling every single `jsonPath(...).value(...)` call, `javac` has to be able to resolve
   **all** overloads of that name in order to decide which one applies — and for that,
   `org.hamcrest.Matcher` itself has to be loadable, even though the `Object` variant always wins in
   the end.
   With Hamcrest gone completely, this breaks with
   `error: cannot access Matcher — class file for org.hamcrest.Matcher not found`.
   Verified live with `mvn clean test-compile`: with `hamcrest-core` excluded from the Testcontainers
   path, the build fails hard.
   What makes this tricky: under incremental compilation
   (no `clean`) the error stayed hidden at first (old `.class` files were reused),
   and even in a full batch compile `javac` reported the error only for the alphabetically
   first affected test class (`AdminUserApiControllerTest`) instead of for all of them — presumably
   a quirk of `javac`'s symbol-completion caching across the several files of one compilation unit.
   Compiled individually with `javac`, *every* test class with this pattern fails identically
   (e.g. `MeApiControllerTest` too) — so the error affects practically the entire
   MockMvc test suite, not just one file.

**Consequence:** the exclusions from `spring-boot-starter-test` stay (they do no harm, since Hamcrest
comes in via Testcontainers anyway), but there is **no** additional exclusion on the
Testcontainers path — the attempt to add one was reverted.
Both `junit:junit` and
`org.hamcrest:*` stay (unused, but structurally necessary) on the
test classpath.

## Decision

- **Assertions: AssertJ** (`assertThat(...)`), throughout.
  For objects where several
  fields are checked, `extracting(...)` / `containsExactly(...)` or soft assertions are preferred (see
  also the team skill *consolidate-test-assertions*), so that a failure shows all the relevant values
  at once.
- **Test doubles: Mockito** (`@Mock`, `@InjectMocks`, `MockitoExtension`; `@MockitoBean` in web
  slices).
  A fixed clock in time-dependent tests via
  `when(timeService.now()).thenReturn(NOW)` (see
  [ADR-0003](0003-time-through-a-timeservice-facade.md)).
- **Engine: JUnit 5** (Jupiter).
- **Web layer**: `MockMvc` with its `status()` / `jsonPath()` / `view()` `ResultMatchers`;
  content assertions go through `andReturn().getResponse().getContentAsString()` + AssertJ (instead
  of Hamcrest's `content().string(matcher)`), since Hamcrest is not on the classpath.
- **Hamcrest**: not used in the source code — no `org.hamcrest.*` matchers, no
  JUnit `Assertions.assertEquals` for domain checks — but physically **not** removable from the
  test classpath, because Spring Test itself needs it for `jsonPath(...).value(...)`
  (see Context).
  That is purely a javac compilability requirement, not a violation of this
  ADR — in the code itself, Hamcrest is nowhere reachable or usable.
- **JUnit 4** (`junit:junit`): no test in the project uses JUnit4 (`@Test`/`@Rule`/`@RunWith` from
  `org.junit.*` instead of `org.junit.jupiter.*`) — checked and confirmed.
  The library itself does stay
  on the test classpath transitively, though, because `org.testcontainers:testcontainers`'s
  `GenericContainer`/`JdbcDatabaseContainer` directly implement the JUnit4 interface
  `org.junit.rules.TestRule`; that cannot be configured away without breaking the MariaDB test
  containers (`*MariaDbTest`) (see Context).
  That is purely JVM class-loading mechanics, not a
  violation of this ADR — in the code itself, JUnit4 is nowhere reachable or usable.

## Consequences

**Easier / better:**

- A single, fluent assertion style; good IDE autocompletion and meaningful
  failure messages, especially for collections (`extracting`, `containsExactly`, `hasValueSatisfying`).
- Consistency across the whole test suite (276 backend tests, as of 2026-07-28).
- Clearly documented and verified live *why* JUnit4 and Hamcrest cannot be removed, instead of
  having to work it out laboriously all over again during some future dependency cleanup.

**Harder / drawbacks:**

- The team has to avoid Hamcrest and `Assertions.assertEquals` — a convention that has to be caught
  in review.
- Some `MockMvc` `ResultMatchers` are designed around Hamcrest matchers; where one would be needed,
  the returned response body is checked with AssertJ instead.
- `junit:junit` AND `org.hamcrest:*` stay (unused) on the test classpath, because
  Testcontainers and Spring Test respectively need them structurally (see Context) — every new
  Testcontainers or Spring Boot version has to be spot-checked again with `mvn dependency:tree`
  or `mvn clean test-compile`, in case either of the two paths changes.

## Alternatives Considered

- **Hamcrest**: verbose matcher syntax, functionally overlaps with AssertJ; deliberately
  excluded to avoid duplication.
- **JUnit's own `Assertions`**: fine for simple cases, but markedly less expressive for
  collections/nested objects.
- **Google Truth**: a solid alternative to AssertJ, but AssertJ is the default that ships with
  Spring Boot — no reason for an extra dependency.
