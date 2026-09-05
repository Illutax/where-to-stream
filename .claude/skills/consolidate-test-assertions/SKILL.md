---
name: consolidate-test-assertions
description: Use when writing or reviewing JUnit/AssertJ tests that check several fields of the same object with multiple assertThat(...) calls in a row. Collapse them into a single soft-style assertion via extracting(...) compared against one expected value, so a failing test reveals all field values at once instead of stopping at the first mismatch.
---

# Consolidate sequential assertions

## Why

A run of single-value assertions fails on the **first** mismatch and hides the rest:

```java
assertThat(first.id()).isEqualTo(1);
assertThat(first.name()).isEqualTo("The Prestige");   // never reached if id() is wrong
assertThat(first.year()).isEqualTo(2006);
```

When `id()` is wrong you re-run, fix it, and only then discover `year()` is also off.
Extracting all the fields and comparing them against one expected value reports **every** discrepancy in a single run, and reads as one intent ("this object equals this").

## The pattern

Build the expected value into a local `var`, extract the same fields, compare once:

```java
final var expected = List.of(
        1,
        "The Prestige",
        "tt0482571",
        URI.create("https://www.imdb.com/title/tt0482571/"),
        2006,
        true);
assertThat(first)
        .extracting(
                ImdbEntry::id,
                ImdbEntry::name,
                ImdbEntry::imdbId,
                ImdbEntry::url,
                ImdbEntry::year,
                ImdbEntry::isRated)
        .isEqualTo(expected);
```

On failure AssertJ prints the full extracted list vs. the expected list, so you see all field values side by side.

## Variants

- **Single object →** `assertThat(obj).extracting(f1, f2, ...).isEqualTo(expectedList)`.
- **Collection of objects →** extract a tuple per element and use `containsExactly`:

  ```java
  assertThat(results)
          .extracting(QueryResult::streamingServiceName, QueryResult::imdbId)
          .containsExactly(
                  tuple("Netflix", IMDB_ID),
                  tuple("Amazon Prime Video", IMDB_ID));
  ```
  (`import static org.assertj.core.api.Assertions.tuple;`)

- **Expected contains `null` →** use `Arrays.asList(...)`, not `List.of(...)` (`List.of` rejects nulls):

  ```java
  final var expectedRent = Arrays.asList(" 3.99 €", " 5.99 €", null);
  assertThat(availability)
          .extracting(a -> a.sd().value(), a -> a.hd().value(), a -> a.fourK().value())
          .isEqualTo(expectedRent);
  ```

- **Derived/nested values →** extractor lambdas may compute, not just reference a getter: `q -> byType(q, RENT).sd().value()`.

## When NOT to apply

Keep assertions separate when they check **different kinds** of properties rather than a tuple of field values — e.g. identity (`isNotSameAs`), presence (`isNotNull` — but see the null-actual caveat below), containment (`contains`), or emptiness on unrelated objects.
Forcing those into one `extracting` chain hurts readability instead of helping.
The pattern is for "several field values of one object/row", not "several unrelated facts".

## What the pattern makes redundant

Assertions that only exist to guard the ones that follow can go:

- **`hasSize(n)` before indexing** — `assertThat(list).hasSize(2)` followed by checks on `list.get(0)`/`getFirst()` is covered by `assertThat(list).extracting(f1, f2).containsExactly(tuple(...), tuple(...))`, which asserts size and content in one go.
- **`assertThat(map.containsKey(k)).isTrue()`** → `assertThat(map).containsKey(k)`.
  The boolean form prints only `expected: true but was: false`; the map form prints the keys that actually are there.

## Caveats

- **A null `actual` bypasses AssertJ's null check.**
  With *several* extractors, `extracting(f1, f2, ...)` applies the extractors to `actual` directly — it never asserts that `actual` is non-null (`AbstractObjectAssert.extractingForProxy` `requireNonNull`s the *extractors* array, not `actual`) — so you get a `NullPointerException` instead of an `AssertionError`.
  Measured with AssertJ 3.27.7 on Java 25:

  | Expression | Result |
  |---|---|
  | `assertThat(null).extracting(P::name, P::age)` | `NullPointerException: null` — **no** helpful message |
  | `assertThat(null).extracting(p -> p.name(), p -> p.age())` | `NullPointerException: Cannot invoke "P.name()" because "<parameter1>" is null` |
  | `assertThat(null).extracting(P::name)` | `AssertionError: Expecting actual not to be null` |
  | `assertThat(null).hasSize(n)` / `.containsExactly(...)` | `AssertionError: Expecting actual not to be null` |

  Note the first row: with a **method-reference** extractor — the form this skill recommends — JEP 358 helpful NPEs do *not* kick in, because the frame comes from a `LambdaMetafactory` hidden class.
  You get a bare `NullPointerException: null`, and the topmost stack frame is `java.util.stream.ReferencePipeline$3$1.accept`, so your own test line is buried under AssertJ frames.
  An explicit lambda does produce a usable message, but still an NPE.
  This is upstream [assertj/assertj#3375](https://github.com/assertj/assertj/issues/3375), open since 2024; the single-extractor variant got the same fix long ago via [#2401](https://github.com/assertj/assertj/issues/2401).
  **Only applies to AssertJ 3.x** — on `main`/4.0.0 `doExtracting` calls `isNotNull("extracting")`, so from 4.0 on the guard below is redundant and can be dropped.

  So when **several** extractors follow, keep the null check — as a link in the same chain, not as a separate `assertThat` call:

  ```java
  assertThat(actual)
          .isNotNull()
          .extracting(Person::name, Person::age)
          .isEqualTo(expected);
  ```

  With a **single** extractor, or when a collection assertion (`hasSize`, `containsExactly`) follows, drop it — AssertJ reports `Expecting actual not to be null` on its own.
- Use exact expected values for deterministic fixtures.
  If a value is environment- or whitespace-sensitive and you can't pin it down, normalize it inside the extractor (e.g. `.trim()`) rather than falling back to many `contains(...)` calls.
- Boxing: `int`/`boolean` getters extract to `Integer`/`Boolean`; put plain `1` / `true` in the expected list — autoboxing and `equals` line up.
