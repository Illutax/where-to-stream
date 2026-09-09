# Contributing

A signpost, nothing more. Everything below already lives somewhere — this file only says where,
because `CLAUDE.md` is not a filename anyone thinks to open.

| Looking for | Go to |
| --- | --- |
| How we work: conventions, testing, prose style, what goes where | [`CLAUDE.md`](CLAUDE.md) |
| Why something is built the way it is | [`docs/adr/`](docs/adr/README.md) — indexed, one decision per file |
| What still needs doing | [`TODOs.md`](TODOs.md) — **open items only** |
| What was done, and what was abandoned and why | [`DONE.md`](DONE.md) — history, not maintained |
| How the architecture stood on a given day | [`docs/reviews/`](docs/reviews/) — dated snapshots |
| Repeatable procedures (opening a ticket, running a review) | [`.claude/skills/`](.claude/skills/) |
| Building and running the thing | [`README.md`](README.md) |

## The one rule worth repeating here

**One fact, one place.** Link the authoritative source instead of restating it. Every copy of a
fact drifts on its own: `ddl-auto` once stood in three documents, two of them still claiming
`validate` long after it had become `none`, and a full clean-up took a day. If you find yourself
writing something that is already written elsewhere, link it.

`DocumentationConsistencyTest` enforces the mechanical half of this during `mvn verify` — paths,
links, ticket numbers, the ADR index. It cannot tell whether a sentence is still *true*.
