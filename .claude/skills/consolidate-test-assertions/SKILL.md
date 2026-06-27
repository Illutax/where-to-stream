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
Extracting all the fields and comparing them against one expected value reports **every**
discrepancy in a single run, and reads as one intent ("this object equals this").

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

On failure AssertJ prints the full extracted list vs. the expected list, so you see all
field values side by side.

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

- **Expected contains `null` →** use `Arrays.asList(...)`, not `List.of(...)`
  (`List.of` rejects nulls):

  ```java
  final var expectedRent = Arrays.asList(" 3.99 €", " 5.99 €", null);
  assertThat(availability)
          .extracting(a -> a.sd().value(), a -> a.hd().value(), a -> a.fourK().value())
          .isEqualTo(expectedRent);
  ```

- **Derived/nested values →** extractor lambdas may compute, not just reference a getter:
  `q -> byType(q, RENT).sd().value()`.

## When NOT to apply

Keep assertions separate when they check **different kinds** of properties rather than a
tuple of field values — e.g. identity (`isNotSameAs`), presence (`isNotNull`),
containment (`contains`), or emptiness on unrelated objects. Forcing those into one
`extracting` chain hurts readability instead of helping. The pattern is for "several
field values of one object/row", not "several unrelated facts".

## Caveats

- Use exact expected values for deterministic fixtures. If a value is environment- or
  whitespace-sensitive and you can't pin it down, normalize it inside the extractor
  (e.g. `.trim()`) rather than falling back to many `contains(...)` calls.
- Boxing: `int`/`boolean` getters extract to `Integer`/`Boolean`; put plain `1` / `true`
  in the expected list — autoboxing and `equals` line up.
