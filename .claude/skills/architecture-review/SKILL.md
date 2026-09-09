---
name: architecture-review
description: Runs an architecture review of the w2s backend and frontend and files the result as a dated snapshot under docs/reviews/. Use when the user asks for an architecture review, for the current state of the architecture, or wants to check whether the ADRs still describe reality.
---

# Architecture review

Produces a **dated snapshot** of the architecture at `docs/reviews/YYYY-MM-DD-architecture-review.md`.

## Why the form is strict

Its predecessor (`docs/reviews/2026-07-28-architecture-review.md`) sat undated under `docs/` and was
therefore read as describing the present.
One day after it was written, ADR-0014 decided the restructuring it had itself triggered — and from
then on the document described a world that no longer existed.
Nobody noticed for months.

Hence two rules that are not negotiable:

1. **Date in the filename, and no edits after writing** (typos excepted).
   A snapshot is allowed to age.
   A "this is the architecture" is not.
2. **The review document is not a backlog.**
   What keeps applying becomes an ADR; what needs doing becomes a TODO.
   The review holds the finding and its reasoning, nothing else.
   Without this rule you get a third document drifting away from `TODOs.md` and `docs/adr/` —
   which is precisely the kind of drift a review is supposed to find.

Written in English, like everything else in the repository (see `CLAUDE.md`).

## Before starting

- Are `TODO-62` (README) and `TODO-64` (cleanups) done?
  If not, ask.
  A review run against documentation already known to be wrong produces findings that already exist
  as tickets.
- Tree green, via the full round in the `run-tests` skill.
  A review on a red tree confuses symptoms with findings.

## Running it

The scope is too large for one pass.
Split it and have subagents work the parts **in parallel** — one self-contained area each,
so an agent can actually read rather than grep:

| Area | Core question |
| --- | --- |
| Context boundaries | Do the four bounded contexts hold? Who reaches past one? Does `ArchitectureTest` cover it? |
| `shared` | Is the `kernel`/`platform` split still right, or has `shared` become the junk drawer? |
| Persistence | Entities, repositories, Liquibase changelog, indexes — does the model still match the usage? |
| Outbound adapters | werstreamt.es, IMDb, TMDB: rate limits, error handling, timeouts, behaviour under failure |
| Frontend | Signals/stores, loading states, shared components, bundle composition |
| **ADR reconciliation** | **Which ADRs no longer describe reality?** Per ADR: does it hold, is it superseded, or is it being broken quietly? |

That last row is the most valuable one and the easiest to forget.
An ADR nobody follows is worse than none — it looks like a guarantee.

Tell every agent:
- It **changes nothing**, it reports.
- Cite `file:line`. No guesses presented as findings.
- Say what it could not check.

## Writing the result

`docs/reviews/YYYY-MM-DD-architecture-review.md`, containing:

- **Header:** date, scope, what was explicitly *not* examined, commit (`git rev-parse --short HEAD`).
- **Per finding:** what, where (`file:line`), why it matters — and whether it confirms an ADR,
  contradicts one, or exposes a gap.
- **No action section.**
  Instead, per finding, a reference to the TODO or ADR it produced.
  Add those references only after the tickets exist, or the document points at nothing.

Then:
1. Open the TODOs in `TODOs.md` (format and rules are in its header, and in the `ticket` skill).
2. Write ADRs via the `adr` skill; set superseded ones to `Superseded` and update the index.
3. Run the full round (`run-tests`) — `DocumentationConsistencyTest` catches dead paths and
   cross-references.

## Cadence

None fixed.
Sensible triggers: a finished larger restructuring, a new bounded context, or the sense that the
ADRs no longer match reality.
A review run "because a quarter has passed" produces prose;
a review after a restructuring produces findings.
