---
name: probe
description: Answers a question about framework or third-party behaviour by running a throwaway experiment instead of reasoning from documentation or memory. Use before acting on any belief about how Spring, Liquibase, Angular, Maven or an external API behaves.
---

# Probe before you believe

When the next step depends on how something *outside this codebase* behaves — Spring's relaxed
binding, Liquibase checksums, what a remote host returns to a non-browser client — the cheapest
correct move is to make it demonstrate the behaviour.
A probe usually costs two minutes.
Being wrong costs a wrong commit plus the time to find out it was wrong.

## When to reach for this

The trigger is a sentence in your own head of the form *"it should…"*, *"I think X does…"*,
*"the docs say…"* — where the answer decides what you write next.
Especially:

- a version, an artefact, a coordinate ("there is no Boot 4 build of this yet")
- a naming or binding convention (env vars, property keys, config precedence)
- whether a tool considers two inputs equal (checksums, caches, idempotence)
- what a remote endpoint actually returns to *you*, rather than to a browser

Skip it when the answer is already in this repository.
Read the code.

## How

1. **State the claim as something that can fail.**
   Not "how does relaxed binding work" but
   "`W2S_SECURITY_INITIAL_ADMIN_USERNAME` binds to `w2s.security.initial-admin.username`".
2. **Use the real machinery, not a mock of it.**
   The probe is only evidence if the thing under test is the thing that will run in production.
3. **Make failure visible.**
   Assert.
   A probe that prints nothing when it fails has told you nothing — and reading silence as success
   is the most expensive habit this project has caught itself in.
4. **Delete it afterwards,** unless the answer was surprising enough that someone will ask again.
   Then it becomes a real test with the finding in a comment.

## Worked examples from this repository

**Does Spring strip dashes from property names when binding environment variables?**
Not read from the docs — bound for real:

```java
var env = new MockEnvironment();
env.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
        "test", Map.of("W2S_SECURITY_INITIAL_ADMIN_USERNAME", "probe")));
var bound = Binder.get(env).bind("w2s.security.initial-admin.username", String.class);
```

This settled a question documentation alone had left ambiguous.
The documented rule is dots→underscores, **dashes removed**, uppercase;
the underscore-for-dash spelling works too, but through a separate fallback.
Two spellings, two mechanisms — and only one of them is the rule I had been about to write down.

**Is a Liquibase `<comment>` part of the changeset checksum?**
Migrated a file-based H2 database, edited the comment of an already-applied changeset, migrated the
same file again.
No validation error, so comments are outside the checksum — which is why
`018-drop-ebay-quota.xml` could be corrected in place instead of needing a follow-up changeset.
File-based, not in-memory: an in-memory database starts empty and would have proved nothing.

**What does eBay return to a script?**
`www.ebay.*` answers automated requests with 403; `m.ebay.*` serves them.
That is how `_sacat=617` and `_sop=15` in `core/ebay-search.ts` were confirmed to mean
"DVDs & Blu-rays" and "price + shipping, lowest first",
rather than being assumed from a URL seen in a browser.

## The failure this exists to prevent

`resilience4j` was dropped from the build on the finding that no Spring Boot 4 compatible release
existed.
One version had been checked: 2.3.0.
2.4.0 existed.
A single sample was reported as a property of the library, and a dependency was deleted on it.

A probe answers a question about the case you tested.
If the conclusion you want to draw is broader than that case, the probe is not finished.
