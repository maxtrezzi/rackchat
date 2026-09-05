# ADR-0002: Track work items in `docs/tasks/`, alongside the ADRs

- **Status:** Accepted
- **Date:** 2026-09-05
- **Supersedes:** —
- **Amends:** —

## Context

ADR-0001 settled how a *decision* gets recorded. It deliberately does not cover "what to do
next" or "is this done" — an ADR that also tries to be a to-do list ends up being edited
after the fact, which conflicts with ADR-0001's immutability rule.

## Forces

- Mixing status into the ADR body means the ADR either goes stale (it says "not started"
  long after the work shipped) or gets edited after acceptance, which ADR-0001 forbids.
- A separate, ordinary tracked document can be updated freely, because it never claims to
  be a permanent record of reasoning — only of state.

## Decision

Work items live in `docs/tasks/`, split by file as the project needs (starting with
`milestones.md` for planned work and `open-decisions.md` for anything blocked on the
owner). A task closes by being marked `Done`, recording what was actually found or built —
not just by being deleted or forgotten. A task that settles a design question closes by
writing an ADR and linking to it; a task that only gets work done closes by being marked
`Done` with a one-line outcome.

## Consequences

`docs/adr/` and `docs/tasks/` answer different questions and must not restate each other:
the ADR says why, the task file says what and whether it is done. Every session should
update `docs/tasks/` in the same commit as the work it describes, the same discipline
modelrack4j follows.
