# Work items

What to do next, and what is done. The reasoning behind *why* the work is shaped this way
lives in [`../adr/`](../adr/README.md) — tasks and ADRs answer different questions and
should not restate each other.

| Question | Answer lives in |
|---|---|
| What do I do next? Is it done? | here |
| Why is it built this way? What did we reject? | [`../adr/`](../adr/README.md) |

A task that settles a design question closes by writing an ADR and linking to it. A task
that merely gets work done closes by being marked `Done`.

## Files

- [`milestones.md`](milestones.md) — M0, M1, … What ships, in what order.
- [`open-decisions.md`](open-decisions.md) — items blocked on the owner, not on work.

## Conventions

**Identifiers never change.** Once a milestone or task is cited elsewhere (an ADR, another
task), renumbering it silently breaks that reference — items are retired in place rather
than renumbered, and new work takes the next free number.

**Status values**

| Status | Meaning |
|---|---|
| `Not started` | Ready to pick up |
| `In progress` | Someone is on it |
| `Blocked` | Waiting on another task; names which one |
| `Needs decision` | Waiting on the owner, not on work |
| `Done` | Finished, with its outcome recorded in the entry |

**Closing a task** means recording what was *found*, not just ticking a box. Findings that
contradict a current ADR trigger an amendment to that ADR (see `../adr/README.md`).

**Every task gets its own branch** ([ADR-0003](../adr/0003-one-feature-branch-per-task.md)).
Branch before starting, never commit to `main`, and name the branch after the item.

## Status board

| Item | What | Status |
|---|---|---|
| [M0](milestones.md#m0--repository-skeleton) | Repository skeleton: `AGENTS.md`, ADRs, task tracking | **Done** |
| [M0.1](milestones.md#m01--backend-build-tooling) | Backend build tooling: Maven, Java 21, minimal Javalin skeleton | **Done** — ADR-0007 |
| [M1](milestones.md#m1--first-runnable-slice) | First runnable slice: connections, streamed chat, the page | **Done** — ADR-0008, ADR-0009; 7 tests green, hot reload seen live |
| [M2](milestones.md#m2--editing-the-configuration-from-the-page) | Editing the configuration from the page | **Done** — ADR-0010, ADR-0011; 11 tests green, round trip driven in a browser |
| [M3](milestones.md#m3--a-chat-that-remembers) | A chat that remembers | **Done** — ADR-0012; 19 tests green, memory made testable with a fake provider |
| [M3.1](milestones.md#m31--startup-failures-read-like-messages-not-crashes) | Startup failures read like messages | **Done** — 22 tests green; two exceptions caught, everything else keeps its trace |
| [M3.3](milestones.md#m33--the-conversation-follows-a-connection-switch) | The conversation follows a connection switch | **Done** — ADR-0013; 26 tests green, the switch driven live against the echo provider |
| [M3.4](milestones.md#m34--modelrack4j-020-snapshot) | modelrack4j `0.2.0-SNAPSHOT`, from the local repository | **Done** — ADR-0014; 27 tests green, an unconfigured start driven live |
| [M3.5](milestones.md#m35--wire-modelrack4j-the-way-it-is-meant-to-be-wired) | Wire modelrack4j the way it is meant to be wired | **Done** — ADR-0015; 28 tests green, a store no longer keeps histories of what it removed |
| [M3.6](milestones.md#m36--the-snapshot-moved-and-brought-a-method-with-it) | The snapshot moved, and brought a method with it | **Done** — 28 tests green on the new jar before any change; `writableSources()` taken up |
