# AGENTS.md

Guidance for a coding agent working in this repository.

## Project state

**Nothing is built yet.** This repository was created on 2026-09-05, and this commit is its
skeleton: documentation and the decision workflow, no code. Read `docs/tasks/milestones.md`
for what comes next.

## What RackChat is

A web application to chat with large language models (LLM), built on
[modelrack4j](https://github.com/maxtrezzi/modelrack4j) and LangChain4j. modelrack4j turns a
layered HOCON configuration file into named, ready-to-use bundles of LangChain4j objects
(a `ChatModel`, `StreamingChatModel`, and so on) — RackChat is the first application built
against it.

The intended shape, agreed before this repository existed and refined as work proceeds:

- A backend that holds a modelrack4j registry, exposes an HTTP API to chat with a selected
  connection, and lets the configuration be edited and saved back through modelrack4j's own
  `store()` API (validated before it is written — never a raw file write).
- A frontend chat page with a selector for which named connection (`SL`, `SH`, `CR`, or
  whatever the configuration defines) to talk to, and a way to edit the configuration.

This is a starting sketch, not a fixed specification — the shape above will be refined, and
disagreements between this file and `docs/adr/` are resolved in favour of the ADRs, which
carry the actual reasoning.

## Decision workflow — follow this every session

Three artifacts, different audiences (this mirrors modelrack4j's own workflow, adopted here
by choice — see ADR-0001):

- **`brainstorm/discussions/YYYY-MM-DD-topic.md`** — local-only, never committed (it is
  git-ignored). Log every substantive design discussion here: what was asked, what was
  weighed, what was rejected and why, what is still open.
- **`docs/adr/NNNN-title.md`** — tracked. Whenever a discussion *settles* something that
  constrains future code — a dependency taken on, an API shape fixed, a scope boundary
  drawn, a mechanism chosen over a real alternative — write an ADR (*Architecture Decision
  Record*). Copy `docs/adr/0000-template.md`, take the next number, follow
  Context → Forces → Decision → Consequences, and add a row to the index in
  `docs/adr/README.md`.
- **`docs/tasks/`** — tracked. Update the status of whatever you worked on, in the same
  commit as the work.

Not every discussion produces an ADR; every discussion produces a log. Recording too little
is the failure mode — a short ADR beats none.

**Accepted ADRs are immutable in their substance.** To change a decision, write a new ADR
and mark the old one `Superseded by ADR-NNNN` (or `Accepted — <aspect> amended by
ADR-NNNN` if only part of it moved). A later finding does not get appended to an ADR — it
goes to `docs/tasks/`.

**ADR numbers are only safe once they are on `main`.** Two branches taking "the next free
number" can collide without either branch seeing it. Renumbering is cheap while nothing is
pushed; check with `grep -rn "ADR-00NN"` across the whole tree, not only `docs/`, after
renumbering anything.

**Branch before starting.** Every task gets its own branch and nothing is committed
directly to `main` (ADR-0003). Name it after the work item —
`task/m1-javalin-skeleton`, `decision/backend-framework`, or `docs/<slug>` for work with
no task ID. The one exception is this very commit: an empty repository has no branch to
start from, so the skeleton in this commit goes directly to `main`. From the next task on,
the rule applies with no exception.

## Build and test

The backend is a Maven project under `backend/`, targeting Java 21 (ADR-0007).

```bash
cd backend && mvn compile   # compile the backend
cd backend && mvn test      # run the backend's tests
```

The frontend build tooling is not decided yet — Hyperapp itself needs no build step, but
whether the project uses one (bundling, TypeScript) is still open
(`docs/tasks/open-decisions.md`).

## Working practices for this repo

- **Verify against upstream sources, never from recollection.** If a library's behaviour
  matters to a decision, check its documentation or its source before writing the ADR that
  depends on it — do not describe an API from memory.
- **The repository is private.** There is no public-audience register to maintain yet;
  when and if that changes, say so here and adjust.
