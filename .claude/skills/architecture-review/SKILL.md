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

- Check `TODOs.md` for open tickets saying the documentation is already known to be wrong
  (the first run of this skill had to wait for `TODO-62`/`TODO-64` for exactly this reason).
  If one is open, ask.
  A review run against documentation already known to be wrong produces findings that already exist
  as tickets.
- Read the open tickets regardless — and hand the list to every review agent.
  A finding that is already tracked gets cross-referenced, not re-found:
  in the 2026-09-10 run `TODO-66` already covered the missing circuit breakers,
  so the new outbound findings landed next to it instead of on top of it.
- Tree green, via the full round in the `run-tests` skill.
  A review on a red tree confuses symptoms with findings.
  Record the evidence (test counts, commit) — it goes into the snapshot header.

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
  The union of these becomes the header's "not examined" list — honesty there is what keeps the
  snapshot quotable.
- Confirmations are findings too.
  "The boundary holds, enforced by rule X at `file:line`" is the evidence behind a
  "the ADRs hold" verdict, and it is what makes the next reconciliation cheap.

The consolidator has duties of its own (learned in the 2026-09-10 run):

- **Re-verify before ticketing.**
  Every finding that becomes a 🔴/🟠 ticket gets an independent spot-check in the source first —
  an agent can misread, and a wrong high-priority ticket costs more than the minutes the check
  takes.
- **Reserve the ticket numbers before writing the document.**
  Next free number = highest across `TODOs.md` **and** `DONE.md`, plus one.
  The document says "→ TODO-NN", each ticket points back at its finding number —
  two-way pointers instead of copied content, per the "point, don't restate" rule.
- **Check every backticked path exists** (one shell loop over both the review and the new
  tickets) before running the build.
  `DocumentationConsistencyTest` fails on a `TODOs.md` path that does not resolve — including a
  bare filename like `application.properties`, which cost the 2026-09-10 run a verify cycle.
  Do not rely on the test for the review document itself; check its paths yourself.

## Writing the result

`docs/reviews/YYYY-MM-DD-architecture-review.md`, containing:

- **Header:** date, scope, what was explicitly *not* examined, commit (`git rev-parse --short HEAD`).
- **Per finding:** a number (F1, F2, … — the tickets reference these), what, where
  (`file:line`), why it matters — and whether it confirms an ADR, contradicts one, or exposes a
  gap.
- **No action section.**
  Instead, per finding, a reference to the TODO or ADR it produced.
  Add those references only after the tickets exist, or the document points at nothing.
- **A decision the review surfaces is not the review's to make.**
  Where an ADR is silent (failure semantics, a growth strategy), the ticket says
  "decide, then extend ADR-NNNN" — the snapshot records that the gap exists, nothing more.
  The 2026-09-10 run left the failure-caching rule and the rate-limit stance open this way
  instead of writing ADRs nobody had agreed to.

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
