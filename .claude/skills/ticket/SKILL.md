---
name: ticket
description: Opens a TODO in TODOs.md, works one, or closes it into DONE.md. Use when opening, updating or closing a TODO/ticket in this repository, or when unsure whether something belongs in TODOs.md, an ADR, or a commit message.
---

# Working a ticket

Two files, two jobs:

| File | Holds | Maintained | Checked |
| --- | --- | --- | --- |
| `TODOs.md` | **open work only** | yes — what is in here is true now | `DocumentationConsistencyTest` |
| `DONE.md` | finished and abandoned | **no**, deliberately | no |

The split is the whole point. Before 2026-09-09 both lived together, 86 % of the lines were
history written in the present tense, and nobody could tell an outdated class name from a claim
that had become false. Ticking a finished entry off in `TODOs.md` instead of moving it recreates
exactly that state.

## Opening one

First the prior question: **does it belong here at all?**

| What | Where |
| --- | --- |
| Something needs doing | `TODOs.md` |
| A decision with reasoning that will keep applying | an ADR (`adr` skill) |
| Why this one change looks the way it does | the commit message |
| How to build and run the project | `README.md` |
| How we work | `CLAUDE.md` |

A ticket that only records an insight, with nobody expected to act on it, is in the wrong place —
it never gets worked and quietly ages.

Then take the next free number across `TODOs.md` **and** `DONE.md` (highest of both, plus one):

```markdown
### 🟠 TODO-N — Short title naming the problem, not the solution
What is the case, and why it matters. Cite paths.

- **Acceptance:** how you can tell it is finished.
```

Plus the row in the overview table at the top of the file, ordered 🔴 → 🟠 → 🟡 → 🟢 and by number
within a level.

### The four rules, and why they exist

1. **Point, don't restate.** Link the authoritative place instead of copying its content.
   `ddl-auto` once stood in three documents — two of them still claiming `validate` long after it
   had become `none`. Every copy of a fact drifts on its own.
2. **Cite paths.** `src/main/java/.../ExportReader.java`, not "somewhere in the service".
   The build checks that the file exists: a backticked path that does not resolve turns the build
   red. Not a formality — this was the single most common kind of drift.
3. **Mark the unverified as unverified.** "Not verified:" is a complete statement. A guess that
   reads like a finding costs more later than it saves: TODO-61 carried a plausible cause for a
   day that turned out wrong, and TODO-14 stayed open for months on a premise that was never true.
4. **No ✅ in `TODOs.md`.** Finished work is moved, not ticked off. The test enforces it.

Everything written into the repository is in English — see the Language section in `CLAUDE.md`.
That says nothing about the language we talk in.

## Working one

- **First check that the description still holds.** Tickets age. A premise that no longer stands
  *is* the finding — then the ticket gets corrected or dropped, not implemented blindly.
- Change as usual: a test with it, `mvn verify` and `ng test` green.
- Something unrelated turning up on the way gets its own ticket, not an appendix to this one.

## Closing one

1. **Save the durable reasoning first**, before moving anything — as an ADR if it will bind future
   decisions. `DONE.md` is not maintained; what lands there is history, and nobody reads it to
   find out how things stand today.
2. Set the marker to ✅ (done) or ❌ (dropped) and add a paragraph: **what was actually done**, and
   where that departed from the original plan. For ❌ above all: why — that is what stops the next
   person starting it again.
3. Move the entry to `DONE.md`, remove its row from the overview table.
4. `mvn verify` — the test catches cross-references that now point nowhere.

References *to* a closed ticket stay valid: the test looks in both files.

## What the test cannot do

It checks paths, links, numbers and markers. Whether a **statement** is still true is invisible to
it — an entry may be green and still talk nonsense. It shrinks what a human has to re-read; it
does not replace the reading.
