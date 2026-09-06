# AGENTS.md

Guidance for a coding agent working in this repository.

## Project state

**M1 is done: the application runs.** A Javalin backend loads a modelrack4j registry from a
configuration file it watches, serves `/api/connections` and a streamed `/api/chat`, and
ships the Hyperapp page that drives them. What is *not* built yet is the configuration
editor — that is M2, and it is blocked on a decision about where the configuration file
lives. Read `docs/tasks/milestones.md` before starting anything; it records what each
milestone actually found, not just that it finished.

**One thing has never run: a live model call.** The machine this was built on has no
provider key and its egress proxy refuses the provider hosts, so every path was exercised
except the one where tokens actually arrive. What *was* seen is the failure half — a
provider error reaching the browser as a clean `done` frame instead of a hang. The first
person to run this with a real key is doing the check nobody has done; treat a bug in the
token path as likely, not surprising.

## What RackChat is

A web application to chat with large language models (LLM), built on
[modelrack4j](https://github.com/maxtrezzi/modelrack4j) and LangChain4j. modelrack4j turns a
layered HOCON configuration file into named, ready-to-use bundles of LangChain4j objects
(a `ChatModel`, `StreamingChatModel`, and so on) — RackChat is the first application built
against it.

The intended shape, agreed before this repository existed:

- A backend that holds a modelrack4j registry, exposes an HTTP API to chat with a selected
  connection, and lets the configuration be edited and saved back through modelrack4j's own
  `store()` API (validated before it is written — never a raw file write). The first half
  exists; `store()` is M2.
- A frontend chat page with a selector for which named connection to talk to, and a way to
  edit the configuration. The selector exists; the editor is M2.

Disagreements between this file and `docs/adr/` are resolved in favour of the ADRs, which
carry the actual reasoning.

## Architecture: what is load-bearing

Short pointers, not the argument — read the ADR before changing any of it.

**The credential must never leave the backend.** `LlmConfig` holds the API key *after*
substitution, so anything that serialises a config or a bundle straight to the browser leaks
it. `ConnectionView` exists to be the only shape that crosses that line, and a test asserts
the payload contains neither the key nor a field named after it. Do not "simplify" the
endpoint to return `bundle.config()`.

**SSE frames carry JSON, not bare text (ADR-0009).** SSE is line-based and strips one space
after `data:`, so a raw token silently loses leading whitespace and every newline. `token`
frames are `{"text": ...}` for that reason alone.

**The browser closes its `EventSource` when `done` arrives (ADR-0009).** An `EventSource`
reconnects by itself when the server hangs up; without that `source.close()` the page would
ask the model the same question again, and pay for it.

**Javalin's SSE handler needs `Accept: text/event-stream` or it does nothing** — an empty
`200 text/plain`, handler never invoked. A browser sends it; a test or a `curl` probe must be
told to. `RackChatApiTest` pins this so the next person meets it as a fact rather than a
mystery.

**RackChat depends on modelrack4j `0.1.0` from Maven Central, not on the copy of that
repository sitting on this machine.** That working copy is `0.2.0-SNAPSHOT` and already has
API `0.1.0` does not (`LlmRegistry.sources()`, `ConfigAccessException`). Check the published
jar with `javap` before using a method — reading modelrack4j's own source will tell you about
methods this project cannot call.

**One `snapshot()` per unit of work, never a cached bundle.** `get()` reads the live snapshot
each call, so two calls can straddle a reload and disagree. The endpoints take one snapshot
and answer from it.

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
`task/m1-first-runnable-slice`, `decision/backend-build-tooling`, or `docs/<slug>` for work
with no task ID. The rule has exactly one exception, already spent: the repository's first
commit went straight to `main`, because an empty repository has no branch to start from.
Everything since has come through a branch.

## Build, test and run

The backend is a Maven project under `backend/`, targeting Java 21 (ADR-0007). The frontend
has no build step at all: it is served from `backend/src/main/resources/public/` and imports
a vendored Hyperapp (ADR-0008).

```bash
cd backend && mvn compile                      # compile
cd backend && mvn test                         # 7 tests, no keys and no network needed
cd backend && mvn test -Dtest=RackChatApiTest  # one class
```

To run it, the backend needs a modelrack4j configuration file. Copy
`backend/rackchat.example.conf`, put your keys in the environment it names, and pass the
path as the first argument (or in `RACKCHAT_CONFIG`; `RACKCHAT_PORT` moves it off 7070):

```bash
cd backend
mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
OPENAI_API_KEY=... java -cp "target/classes:$(cat target/cp.txt)" \
  io.github.maxtrezzi.rackchat.Main rackchat.conf
# then open http://localhost:7070/
```

There is no `exec:java` or shaded jar yet — add one when running it stops being a thing done
by hand.

**The tests need no API key and no network**, because building a bundle never calls the
provider: the fake key in `RackChatApiTest` only fails at the first request, which no test
makes. That is a property of modelrack4j, not luck, and it is what keeps the suite offline.

**Editing the configuration file while the server runs is the point, not a trick.** The
registry is built with `watch(true)`, so adding a block makes the connection appear in
`/api/connections` about a second later, with no restart. If a change to the file is
rejected, the previous configuration stays live and the rejection is logged.

## Working practices for this repo

- **Verify against upstream sources, never from recollection.** If a library's behaviour
  matters to a decision, check its documentation or its source before writing the ADR that
  depends on it — do not describe an API from memory.
- **The repository is private.** There is no public-audience register to maintain yet;
  when and if that changes, say so here and adjust.
