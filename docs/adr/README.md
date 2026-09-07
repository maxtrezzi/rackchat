# Architecture Decision Records

One file per decision, numbered sequentially, never deleted. A decision that turns out
wrong is not edited away — a new ADR supersedes it and both stay in the history.

## Format

`NNNN-kebab-case-title.md`, starting at `0001`. Copy `0000-template.md`.

The body follows a **Context → Forces → Decision → Consequences** shape: what was being
decided and why then, what pressures made it a real choice, what was chosen, and what the
project now lives with. Consequences include the costs, not only the benefits — an ADR
that lists no downside was not a decision.

The header carries `Supersedes` (this ADR replaces one wholesale) and `Amends` (this ADR
narrows or widens part of one). Both default to `—`.

## Status values

| Status | Meaning |
|---|---|
| `Proposed` | Written up, not yet agreed |
| `Accepted` | In force; implementation must follow it |
| `Accepted — <aspect> amended by ADR-NNNN` | Still in force, but a later ADR narrowed or widened part of it; the later ADR wins where they differ |
| `Superseded by ADR-NNNN` | Replaced wholesale; kept for the reasoning trail |

Superseding or amending an ADR means editing only the old file's `Status` line and adding
the pointer. Its body stays untouched — including the parts the newer ADR overrode, since
those are what make the change legible.

**The body is also frozen against additions.** A measurement that confirms an ADR, a
finding that came later, a note that something has since changed — none of these are
appended here, no matter how clearly dated. They belong in
[`../tasks/`](../tasks/README.md).

## When to write one

Whenever a discussion settles something that constrains future code: a dependency taken
on, an API shape fixed, a scope boundary drawn, a mechanism chosen over an alternative
that was genuinely considered. Not for reversible implementation details, and not for
things the code states plainly on its own.

Discussion transcripts and half-formed thinking do **not** belong here — those go to
`brainstorm/discussions/`, which is local-only and never committed. An ADR is the
distilled, rewritten result, safe to publish.

Work items do not belong here either. What to do, and whether it is done, lives in
[`../tasks/`](../tasks/README.md) (ADR-0002); an ADR explains why the work is shaped the
way it is.

## Index

**Process**

| ADR | Title | Status |
|---|---|---|
| [0001](0001-record-decisions-as-adrs.md) | Record decisions as ADRs; keep discussion logs out of the repository | Accepted |
| [0002](0002-track-work-items-in-docs-tasks.md) | Track work items in `docs/tasks/`, alongside the ADRs | Accepted |
| [0003](0003-one-feature-branch-per-task.md) | One feature branch per task | Accepted |

**Architecture**

| ADR | Title | Status |
|---|---|---|
| [0004](0004-build-on-modelrack4j-for-llm-connections.md) | Build on modelrack4j for LLM connection management, not a bespoke integration | Accepted |
| [0005](0005-javalin-as-the-backend-web-framework.md) | Javalin as the backend web framework | Accepted |
| [0006](0006-hyperapp-as-the-frontend-library.md) | Hyperapp as the frontend library | Accepted |
| [0007](0007-maven-and-java-21-for-the-backend.md) | Maven and Java 21 for the backend build | Accepted |
| [0008](0008-the-frontend-ships-inside-the-backend-jar.md) | The frontend ships inside the backend jar, as classpath static files | Accepted |
| [0009](0009-stream-the-answer-over-server-sent-events.md) | Stream the answer over Server-Sent Events, framed as JSON | Accepted |
| [0010](0010-edit-the-raw-hocon-in-a-textarea.md) | Edit the raw HOCON in a textarea, saved with an optimistic check | Accepted |
| [0011](0011-a-writable-layer-with-a-notifier-supplied-by-hand.md) | A writable layer with the file notifier supplied by hand | Accepted |
| [0012](0012-memory-lives-on-the-server-one-per-conversation-and-connection.md) | Memory lives on the server, one per conversation and connection | Accepted — amended by [0013](0013-carry-the-conversation-across-a-connection-switch.md) |
| [0013](0013-carry-the-conversation-across-a-connection-switch.md) | Carry the conversation across a connection switch by seeding the new memory | Accepted |
