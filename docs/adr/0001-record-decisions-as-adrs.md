# ADR-0001: Record decisions as ADRs; keep discussion logs out of the repository

- **Status:** Accepted
- **Date:** 2026-09-05
- **Supersedes:** —
- **Amends:** —

## Context

RackChat starts from a conversation between the owner and a coding agent, not from a
written specification. Several choices were already made in that conversation before any
code existed — the backend framework, the frontend library, the fact that the project
depends on modelrack4j — and more will follow the same way. Without a place to write the
reasoning down, later sessions (human or agent) would have to re-derive *why* a choice was
made, or worse, silently assume it was arbitrary and change it.

The owner also maintains `maxtrezzi/modelrack4j`, which uses a three-artifact workflow for
exactly this problem (its own ADR-0001 and ADR-0015). Reusing a workflow that already works,
rather than inventing a new one, was itself part of this conversation.

## Forces

- A full transcript of every design conversation is honest but unreadable months later —
  it mixes the settled conclusion with the rejected alternatives and the back-and-forth
  that got there.
- A decision with no record at all is worse: a future session sees Javalin, or Hyperapp, or
  a dependency on modelrack4j, and has no way to tell whether that was reasoned or
  accidental.
- The conversation itself may contain material the owner does not want committed to a
  repository (half-formed ideas, wrong turns, anything written before it was checked).

## Decision

Adopt the same three-artifact split modelrack4j uses:

- `brainstorm/discussions/` — the raw discussion log, local-only, never committed.
- `docs/adr/` — the distilled, rewritten decision, safe to publish, following
  Context → Forces → Decision → Consequences (template in `0000-template.md`).
- `docs/tasks/` — what to do and whether it is done, separate from why.

An ADR is written by *rewriting* the relevant part of the discussion log, never by copying
it. Accepted ADRs are immutable in their substance: a later change is a new ADR marked
`Supersedes` or `Amends`, not an edit to the old one.

## Consequences

Every subsequent architectural choice in this project gets an ADR before it is acted on,
not after. The cost is overhead on decisions that turn out not to matter much — the
project accepts that cost because the alternative, found in modelrack4j, is a document that
drifts and no longer reflects what the code does. `brainstorm/` must stay in `.gitignore`
and must never be committed, quoted into a commit message, or pasted into a tracked file —
doing so would defeat the reason it exists.
