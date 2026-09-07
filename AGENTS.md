# AGENTS.md

Guidance for a coding agent working in this repository.

## Project state

**M1 to M3 are done: the application runs, it remembers a conversation, and its configuration
can be edited from the page.** A Javalin backend loads a modelrack4j registry from a
configuration file it watches, serves `/api/connections`, a streamed `/api/chat` that carries
the conversation's history, and `GET`/`PUT /api/config`; the Hyperapp page has a chat tab and
a configuration tab. Read `docs/tasks/milestones.md` before starting anything; it records what
each milestone actually found, not just that it finished.

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
  `store()` API (validated before it is written — never a raw file write).
- A frontend chat page with a selector for which named connection to talk to, and a way to
  edit the configuration.

Both exist, and M3 added the part the original sketch never mentioned: the conversation is
kept, so a follow-up question builds on the answer before it.

Disagreements between this file and `docs/adr/` are resolved in favour of the ADRs, which
carry the actual reasoning.

## Architecture: what is load-bearing

Short pointers, not the argument — read the ADR before changing any of it.

**A *substituted* credential must never leave the backend.** `LlmConfig` holds the API key
after substitution, so anything that serialises a config or a bundle straight to the browser
leaks it. `ConnectionView` exists to be the only shape that crosses that line, and a test
asserts the payload contains neither the key nor a field named after it. Do not "simplify"
the endpoint to return `bundle.config()`.

**The editor is the deliberate exception, and it is why the server binds loopback
(ADR-0010).** `/api/config` serves the configuration file's raw text, because you cannot edit
what you cannot see. With `api-key = ${OPENAI_API_KEY}` that exposes a variable's name; with a
literal key pasted into the file it exposes the key. Nothing authenticates, so the default
bind address is `127.0.0.1`. Changing that (`RACKCHAT_HOST`) without solving authentication
puts the configuration, and anything literal in it, on the network.

**A save validates before it writes, and that ordering is the point (ADR-0010).**
`storeIfUnchanged` parses and publishes the whole configuration first, and only then replaces
the file — so text that would not load is refused instead of being saved and breaking the next
start. A test asserts the file is byte-identical after a rejected save. Never replace this
with "write the file, then reload".

**A save has three refusals and three answers.** `409` for a layer that moved under the
editor, `400` for text that would not load, `500` for a file that could not be written.
`ConfigValidationException` and `ConfigAccessException` are separate types in modelrack4j
`0.2.0` and neither is the other's subclass (ADR-0014), which is what makes the last two
distinguishable — telling someone to fix their text when the directory is read-only sends them
to look in the wrong place.

**The registry is wired by hand (ADR-0011).** `sources(writable)` plus an explicit
`FileChangeNotifier`, not `configFiles(...)` with `watch(true)` — because `store()` needs a
source from `sources(...)`, and modelrack4j `0.1.0` refused `watch(true)` on a registry built
that way. **`0.2.0` accepts it** (its ADR-0050), so the hand-wiring is now a choice rather
than the only route; it stays until someone decides otherwise, because collapsing a mechanism
that works and is tested is a decision, not a tidy-up. What has not changed: a registry built
with `configFiles(...)` alone has no writable source, and that silently removes the editor.

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

**RackChat depends on modelrack4j `0.2.0-SNAPSHOT` from the local Maven repository
(ADR-0014).** Nothing resolves it from Maven Central: it gets there by `mvn install` in the
modelrack4j working copy on this machine, so a checkout without that build does not compile.
Check the installed jar with `javap` before using a method — a snapshot's API is allowed to
move, and the jar in `~/.m2` is the only statement of what this project can call today:

```bash
javap -cp ~/.m2/repository/io/github/maxtrezzi/modelrack4j-core/0.2.0-SNAPSHOT/modelrack4j-core-0.2.0-SNAPSHOT.jar \
  io.github.maxtrezzi.modelrack4j.LlmRegistry
```

**Its types carry a parameter.** `LlmRegistry`, `LlmBundle` and `LlmSnapshot` are generic in
whatever a `CustomPropertiesHandler` produces. RackChat registers none: it builds an
`LlmRegistry<Void>` and takes `<?>` wherever the type is irrelevant, which is everywhere else.
Never write them raw — a raw type erases every generic member of the class, including the
`Optional<ReloadChange>` a `store` returns.

**A registry may hold nothing, so RackChat starts unconfigured.** `0.2.0` accepts a
configuration that defines no connection — an empty file, comments only, or `llm {}` — and
builds a registry whose `names()` is empty (its ADR-0057). RackChat starts on it, serves an
empty `/api/connections`, says so in the page, and its editor is reachable: starting from
nothing and filling the file in from the page works. `get(...)` on a name no layer defines
throws `UnknownConfigurationException`, which is why the chat endpoint asks
`snapshot.contains(name)` first.

**An unknown key is now an error** (its ADR-0056). A misspelling that `0.1.0` ignored in
silence — `temperatur`, `max-mesages` inside `memory` — is refused at load, naming the key,
the layer and the line. A configuration that worked before can stop working, and that is the
version bump, not a defect.

**One `snapshot()` per unit of work, never a cached bundle.** `get()` reads the live snapshot
each call, so two calls can straddle a reload and disagree. The endpoints take one snapshot
and answer from it.

**`main` catches exactly two exceptions, and that number is the point.**
`ConfigValidationException` and `JavalinBindException` become messages with exit code `1`,
because they are the two mistakes a first run actually makes. Everything else keeps its stack
trace: an unexpected exception is a defect, and widening this catch would turn a bug report
into a shrug.

**Memory comes from the bundle, never from RackChat (ADR-0012).** `Conversations` decides
*which* memory a request belongs to — one per (conversation, connection) — and nothing else.
A connection with no `memory` block gets none, and the page says so: do not add a default
here, because that would be the application overruling a configuration that said nothing.
Memory is written only when an answer comes back, so a failed call leaves no dangling
question.

**A switch carries the conversation, through the new connection's own `add` (ADR-0013).**
The first time a conversation reaches a connection, its memory is seeded with the history of
the connection that conversation last answered on — replayed message by message, so the
receiving `memory` block still decides what survives. Copying the message list instead would
put text in front of a model that its own window excludes, which is exactly the guarantee
ADR-0012 exists for. Seeding happens once, at creation: memories are not kept in step
afterwards.

**The one way to see whether the model was told anything is `EchoProviderFactory`** in test
scope: a modelrack4j provider whose model answers with the messages it received. Its answers
render as a bare `ai` with no text **on purpose** — an earlier answer is itself such a
description, and including its text nests every turn inside the next, which quietly makes any
substring assertion about eviction come out true. Run the app with it by putting
`target/test-classes` on the classpath and `provider = echo` in the configuration.

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
cd backend && mvn test                         # 27 tests, no keys and no network needed
cd backend && mvn test -Dtest=RackChatApiTest  # one class
```

To run it, the backend needs a modelrack4j configuration file. Copy
`backend/rackchat.example.conf`, put your keys in the environment it names, and pass the
path as the first argument (or in `RACKCHAT_CONFIG`; `RACKCHAT_PORT` moves it off 7070,
`RACKCHAT_HOST` off `127.0.0.1` — read ADR-0010 before doing that):

```bash
cd backend
mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
OPENAI_API_KEY=... java -cp "target/classes:$(cat target/cp.txt)" \
  io.github.maxtrezzi.rackchat.Main rackchat.conf
# then open http://localhost:7070/
```

There is no `exec:java` or shaded jar yet — add one when running it stops being a thing done
by hand. `docs/running-locally.md` is the user-facing version of all this, written for someone
starting from a fresh checkout; keep the two in step, and prefer sending a reader there rather
than repeating its content.

**The tests need no API key and no network**, because building a bundle never calls the
provider: the fake key in `RackChatApiTest` only fails at the first request, which no test
makes. That is a property of modelrack4j, not luck, and it is what keeps the suite offline.

**Editing the configuration while the server runs is the point, not a trick**, and it works
from both directions. Editing the file on disk makes the connection appear in
`/api/connections` about a second later, with no restart; editing it in the page's
configuration tab does the same through `store()`. If a change is rejected, the previous
configuration stays live — from the file, the rejection is logged and the old configuration
keeps serving; from the page, the save is refused with the reason and the file is untouched.

## Working practices for this repo

- **Verify against upstream sources, never from recollection.** If a library's behaviour
  matters to a decision, check its documentation or its source before writing the ADR that
  depends on it — do not describe an API from memory.
- **The repository is private.** There is no public-audience register to maintain yet;
  when and if that changes, say so here and adjust.
