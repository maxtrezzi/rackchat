# ADR-0003: One feature branch per task

- **Status:** Accepted
- **Date:** 2026-09-05
- **Supersedes:** —
- **Amends:** —

## Context

RackChat is worked on by a coding agent across many separate sessions, sometimes with no
human review in between. Committing straight to `main` makes every session's half-finished
work immediately live, and makes it hard to tell which commit belongs to which task later.

## Forces

- A solo, private project has no reviewer forcing a pull request — so the discipline has
  to be self-imposed to have any effect at all.
- Branching has a real cost only when it is skipped inconsistently; applied uniformly it
  is close to free.
- The very first commit to this repository cannot follow the rule literally: an empty
  repository has no branch to start from.

## Decision

Every task gets its own branch, named after the work item (`task/<slug>`,
`decision/<slug>`, `docs/<slug>`), and nothing is committed directly to `main` except the
one bootstrap commit that creates this file — from the next task onward the rule applies
with no exception. A branch may carry several `docs/tasks/` entries when they are genuinely
one piece of work, following the convention modelrack4j documents in its own
`docs/tasks/README.md`.

## Consequences

A branch that matches no task identifier should read as a deliberate grouping, documented
as such, rather than as drift. If branch protection is added to `main` later, this rule is
already the practice it would enforce — adding the check changes nothing about how work is
done, only who could bypass it.
