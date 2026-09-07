# Milestones

## M0 — Repository skeleton

**Status: Done.**

What this milestone is: `AGENTS.md`, the decision workflow (`docs/adr/`, `docs/tasks/`,
`brainstorm/discussions/` as a local-only convention), and the ADRs recording the choices
already made in conversation before this repository existed — modelrack4j as the
connection layer (ADR-0004), Javalin as the backend (ADR-0005), Hyperapp as the frontend
(ADR-0006). No application code yet.

## M0.1 — Backend build tooling

**Status: Done.**

Maven project under `backend/`, Java 21 (ADR-0007), a minimal Javalin app with a `/health`
endpoint and one test, proving the toolchain works end to end (`mvn compile`, `mvn test`
green, 1 test run). No modelrack4j integration and no chat logic yet — that is M1.

**Found while building this:** Javalin 7 moved routing out of the running `Javalin`
instance. `app.get(...)` (Javalin 6 style) no longer compiles — routes are registered in
the config block instead: `Javalin.create(config -> config.routes.get("/path", handler))`.
Confirmed against the 6-to-7 migration guide, not assumed from an older recollection of the
API.

## M1 — First runnable slice

**Status: Done.**

What shipped: a Javalin backend that loads a modelrack4j `LlmRegistry` from a configuration
file and watches it, `GET /api/connections`, a streamed `GET /api/chat` over Server-Sent
Events (ADR-0009), and a Hyperapp page with a connection selector, a message box and the
reply as it arrives (ADR-0008 puts that page inside the backend jar). All four modelrack4j
providers are on the classpath at runtime scope, so any configuration the library accepts
works without touching the build. 7 tests, green.

The connection **selector** landed here rather than in a later milestone — it costs one
`select` element once `/api/connections` exists. The configuration **editor** did not: it
needs `store()` and a decision about where the file lives, which is M2.

**What was verified by running it, not by reading:**

- **Hot reload works end to end.** With the server running, appending a block to the
  configuration file made the new connection appear in `/api/connections` about a second
  later, with the watcher logging `[careful] added`. No restart. That is the whole reason
  for ADR-0004, now demonstrated rather than assumed.
- **Javalin's SSE handler does nothing without `Accept: text/event-stream`.** The request
  gets an empty `200` with `Content-Type: text/plain` and the handler never runs. Two tests
  failed on this and **the tests were wrong, not the code** — `curl` with the header
  produced correct frames immediately. A browser's `EventSource` always sends it, so this
  only bites hand-written clients. There is now a test that pins the empty-response
  behaviour, so the next person meets it as a documented fact.
- **The page works in a real browser.** Driven headless with Playwright: the selector fills
  from the API, sending a message creates both bubbles, and a failing model call surfaces as
  an error in the page instead of hanging. It also found a `404` on `/favicon.ico` on every
  load, now silenced with an empty `data:` icon.
- **The API key never reaches the browser.** A test asserts the connections payload contains
  neither the key nor a field named after it. `LlmConfig` holds the credential after
  substitution, so anything that serialises a config or a bundle wholesale would leak it.

**What was not verified: a live model call.** This machine has no provider key and its
egress proxy refuses the provider hosts, so no token has ever come back from a model. The
streaming handler was exercised through its *error* path — a blocked request produced
`{"error":"java.io.IOException: Tunnel failed, got: 403"}` in a `done` frame, and the page
showed it instead of hanging — which proves the plumbing but not the happy path. The first
run with a real key is a genuine test, not a formality.

**On the modelrack4j version:** the dependency is `0.1.0` from Maven Central, and its API
was read from the published jar with `javap` — not from the working copy of modelrack4j on
this machine, which is `0.2.0-SNAPSHOT` and already has methods `0.1.0` does not
(`LlmRegistry.sources()`, `ConfigAccessException`). `0.1.0` brings LangChain4j `1.19.0`.

## M2 — Editing the configuration from the page

**Status: Done.**

Both open questions were settled by the owner on 2026-09-06: a textarea over the raw HOCON,
and the file stays where it is (an argument or `RACKCHAT_CONFIG`). What shipped:
`GET /api/config`, `PUT /api/config` through `storeIfUnchanged`, and a second tab in the page
with the text, a Save and a Reload (ADR-0010). 11 tests, green.

**What the probes established before any code was written** — all three ran against the
published `0.1.0` jar, not against the newer working copy of modelrack4j on this machine:

- **`sources(writable)` + `watch(true)` is refused in `0.1.0`**, with
  *"watch(true) watches configuration files, and this registry has none — its layers were
  given through sources(...)"*. Since `store()` needs a writable source and a writable source
  can only arrive through `sources(...)`, the editor and hot reload looked mutually exclusive.
- **The error message's own suggestion works.** `FileChangeNotifier.of(List, Duration)` is
  public in `0.1.0`; with it, the registry builds, an outside edit fires `onReload` and
  changes `names()`, and `store()` still works. That is ADR-0011.
- **A store fires no reload listener.** `store()` returned
  `ReloadChange[added=[viaStore]]` to its caller and the `onReload` listener was called
  **0** times, because the publish happens before the write and the watcher then sees an
  empty diff. So the page refetches the connections itself.
- **A rejected store touches nothing.** Invalid text left the file byte-identical and the
  registry still serving its old connections.
- **`storeIfUnchanged` with a stale expectation throws `StaleLayerException`**, and
  `current()` hands back the text the file actually holds.

**What was verified in the browser, end to end:** the editor loads the file's text; a broken
save is refused with modelrack4j's own message (*"llm.broken.provider is 'nosuchprovider',
for which no provider module is on the classpath"*) and Reload restores the file's text
exactly; a good save reports success, the file on disk gains the block, and the new
connection is selectable in the chat tab without a restart.

**What could not be tested here:** a genuine write failure. The probe made the directory
read-only and the store succeeded anyway — this container runs as **root**, and root ignores
those permission bits. So the claim in ADR-0010 that a write failure surfaces as a `400` comes
from modelrack4j's documentation of its own `0.1.0`, not from a measurement.

**A safety consequence, recorded because it is easy to miss:** the editor serves the file's
raw text, so a literal key pasted into the file reaches the browser, and nothing
authenticates. The server now binds `127.0.0.1` by default; `RACKCHAT_HOST` changes it and
should not be changed casually.

## M3 — A chat that remembers

**Status: Done.**

Until here each request sent one message and nothing else, so the `memory` block changed
nothing about what the model saw. Now the server keeps one `ChatMemory` per (conversation,
connection), taken from the bundle's own provider (ADR-0012); the page generates a
conversation id, offers "New conversation", and says under the selector whether the chosen
connection remembers. 19 tests, green.

**The important part is that this became testable at all.** "The model remembered" cannot be
observed from outside without a live provider, and there is no key here. So the test tree now
carries `EchoProviderFactory`, a modelrack4j `ProviderFactory` registered through
`META-INF/services` in **test scope**, whose model answers with a description of the messages
it was given: `saw 3: user=one|ai|user=two`. That makes the whole feature checkable offline,
end to end through the real HTTP endpoint. The same trick runs the app by hand — put
`target/test-classes` on the classpath and `provider = echo` in the configuration.

**Two test failures that were the tests' fault, both worth remembering:**

- Adding the `conversation` parameter made an M1 test fail, because it asserted the error for
  an unknown connection and now got "conversation is required" first. Deliberate behaviour
  change, stale assertion.
- The window test failed twice while the code was correct. The fake model originally answered
  by quoting the messages it saw **including the text of earlier answers**, so each turn
  nested the previous one and any `contains("user=one")` was true regardless of what had been
  evicted. The fix was in the fake, not the assertion: an answer now renders as a bare `ai`
  with no text, so the rendering cannot nest. A fake that echoes its input recursively cannot
  be asserted on with substrings.

**Verified in the browser** (with the echo provider): the second question arrives as
`saw 3: user=first question|ai|user=second question`; "New conversation" clears the transcript
and the next question arrives as `saw 1:`; a connection with no `memory` block stays at
`saw 1:` forever and says so in the page. Two apparent UI bugs during that check —a hint
showing the previous connection, a transcript that did not clear — were both the check script
reading the DOM in the same tick as the click; re-reading after the render showed the page
correct.

**Still not verified:** any of this against a real model. That gap is the same one M1 left.

## M3.1 — Startup failures read like messages, not crashes

**Status: Done.**

Writing `docs/running-locally.md` meant running a first start from a clean clone, which
showed the most likely first-run mistake — forgetting to export the key variable — arriving as
a Java stack trace. modelrack4j's message was already the right one
(*"Set the environment variable, or override the value in a higher-precedence layer"*); it was
just buried.

`main` now catches exactly two exceptions and prints them as messages, with exit code `1`
(measured — the first attempt read `$?` from a `grep` at the end of a pipeline and reported a
misleading `0`):

- `ConfigValidationException` — anything the configuration got wrong.
- `JavalinBindException` — the port is taken, with `RACKCHAT_PORT` named as the way out.

**Everything else keeps its stack trace on purpose.** An unexpected exception is a defect, and
the trace is the useful part of it; catching broadly here would turn a bug report into a
shrug. Javalin still logs its own `Failed to start Javalin` line before the port message —
its logging, not ours, and the message lands last.

`Main.explain` folds in the cause's message only when it adds something, because modelrack4j
already embeds the underlying reason in its own text and the naive version printed the same
sentence twice. Tested against the real exception rather than a stand-in: the test builds a
registry over a config with an unset variable, and asserts the rendered text names the
variable and contains no stack frames. 22 tests, green.

## M3.2 — A launch script

**Status: Done.**

`backend/run.sh` compiles, regenerates the dependency classpath and starts `Main` with the
arguments it was given. It is not packaging and does not pretend to be — there is still no
shaded jar and no `exec:java`; it wraps the three lines that never vary so that only the
configuration path and the environment are typed.

The fake provider is the other half. `provider = echo` resolves only against
`target/test-classes`, so the script reads the configuration it is about to start on and
compiles the test sources when that configuration names it. Needing the fake is a property of
the configuration, not of the run, and the script asks the same file RackChat will.

A flag was the first shape of that — and a trap, because the configuration a bare `./run.sh`
picks up by default is the echo one, so the two defaults contradicted each other and the
plain command could only fail. `--echo` survives as a way to force the test sources on for a
file that has no echo block yet but is about to be given one from the editor.

**Found while building this: RackChat cannot start on an empty configuration file.**
modelrack4j refuses a registry with no connections — `No 'llm' block found in any
configuration layer`, and `The 'llm' block is empty: no configurations to build` for an
`llm {}` with nothing under it. Both come from `SnapshotLoader`, which carries the same check
in the `0.2.0-SNAPSHOT` working copy.

That closes a route which looks obvious from the outside: start with nothing, then fill the
configuration in from the editor. It cannot work, because the page holding the editor is only
reachable once a configuration has loaded. Expressing "running, nothing configured yet" on
top of `0.1.0` would mean holding an optional registry, a bootstrap watcher to build one when
the file gains its first connection, and a second path through save for text that defines
none — a component RackChat would carry until the library changed. Allowing an empty rack
upstream removes all of it, and that is where the fix is going; the fake `echo` connection
covers a first start meanwhile, being the only kind that needs no account.

**Verified end to end** with `./run.sh` over an echo configuration: the log reports `knows 1
connection(s): [echo]`, `/api/connections` answers with the `echo` view and no `api-key` field
in it, and two questions in one conversation come back as `saw 1: user=first` then
`saw 3: user=first|ai|user=second` — the second counting the question, its answer and the new
question, so memory is live. No key and no network were involved.

The other branch was checked too: over a configuration naming `provider = openai` with a
literal fake key, the same command starts on `target/classes` alone, with no test classes in
the running process's `-cp`. Building a bundle never calls the provider, which is why a key
that cannot work still starts.

## M3.3 — The conversation follows a connection switch

**Status: Done.**

Implements [ADR-0013](../adr/0013-carry-the-conversation-across-a-connection-switch.md): a
conversation reaching a connection it has not used before is handed the history it already
has, instead of starting empty.

`Conversations` gained a second map — which connection each conversation last *completed an
exchange* on — and seeds a memory at creation by replaying that source's messages through the
new memory's own `add`. Following completed exchanges rather than requests is what keeps a
failed call from becoming the source of a history it never received, which is also why
`remember` moved out of `RackChatApi`: the write and the pointer that follows it belong under
the same lock, and splitting them across two classes would have left the second easy to
forget. `forget(removed)` now drops those pointers too, so a reload cannot leave one aimed at
a connection that no longer exists.

The page's note under the selector distinguishes the two states it now has: a connection that
is already keeping this conversation says "remembers this conversation", one about to be
handed it says where the history comes from — `remembers this conversation, starting from what
cheap kept`. It follows completed answers as the server does, so a failed question does not
move it.

**26 tests, green.** Five are new, and one was replaced rather than added to:
`switchingConnectionStartsThatConnectionsOwnHistory` asserted the behaviour ADR-0013 reverses,
so it became `switchingConnectionCarriesTheConversation`. The rest pin what the ADR promises
and what it does not: a `max-messages = 2` receiver is given only the last exchange of a
longer carried history, a connection with no `memory` block is handed nothing, a turn on such
a connection leaves nothing for the next one to carry, and switching back finds the first
memory as it was left — with its own turn and no copy of what the other connection was told.

**Verified live** against the echo provider, two connections in one conversation: `cheap`
answered `saw 1: user=first`, `strong` answered `saw 3: user=first|ai|user=second` after the
switch, `cheap` answered `saw 3: user=first|ai|user=third` on the way back — its own history,
not the one `strong` had grown — and a second conversation on `strong` answered `saw 1:
user=alone`. No key and no network.

**What stays open:** the carry has only been seen on `message-window` memories. The cost the
ADR accepts — one eviction pass at the switch — is only visible on a `token-window` memory
over a provider with a remote estimator, which is another thing this machine cannot exercise.

## M3.4 — modelrack4j 0.2.0-SNAPSHOT

**Status: Done.**

`modelrack4j.version` is `0.2.0-SNAPSHOT`, resolved from the local Maven repository rather
than Maven Central, decided in
[ADR-0014](../adr/0014-build-against-the-local-modelrack4j-0-2-0-snapshot.md). Building
RackChat now needs `mvn install` in the modelrack4j working copy first; that prerequisite is
in `docs/running-locally.md`.

**The compile breaks in a way a raw type hides.** `LlmRegistry`, `LlmBundle` and `LlmSnapshot`
are generic now. Leaving them raw still compiles the declaration and then erases every generic
member of the class, so `onReload(change -> change.added())` fails with `change` inferred as
`Object` and `snapshot.names()` stops producing `String`. RackChat builds an `LlmRegistry<Void>`
where it constructs one and takes `<?>` everywhere else, since it registers no
`CustomPropertiesHandler` and never reads custom properties.

**A failed write is no longer an editor mistake.** `ConfigAccessException` is a separate type
from `ConfigValidationException` and neither is the other's subclass, so `/api/config` answers
`400` for text that would not load and `500` for a file it could not write. **27 tests** — the
new one makes the temporary directory read-only, saves valid text, and asserts `500` and a
byte-identical file. Without the split that save would have been reported as text to fix, and
there is nothing in the editor to fix.

**Verified live: RackChat starts unconfigured.** With `llm {}` — and with a completely empty
file — it starts, logs `knows 0 connection(s): []`, and serves an empty `/api/connections`. A
`PUT /api/config` adding an `echo` block was accepted, the connection appeared without a
restart, and it answered `saw 1: user=hello`. This is the route M3.2 recorded as closed, and
it is the reason the upstream change was made.

**Not taken up in this move:** `watch(true)` now accepts layers given to `sources(...)`, so
ADR-0011's hand-wired `FileChangeNotifier` is no longer the only way to have both an editor
and hot reload. Collapsing it removes a mechanism that works and is tested, which is its own
decision — see `open-decisions.md`.

## M3.5 — Wire modelrack4j the way it is meant to be wired

**Status: Done.**

Three things RackChat was doing for itself that the library does, or does better. Decided in
[ADR-0015](../adr/0015-let-the-registry-watch-its-layers-and-hand-back-the-writable-one.md),
which amends ADR-0011; read modelrack4j's own reference alongside it, since two of the three
are stated there as the intended use.

**The watcher is the library's.** `sources(writable)` plus `watch(true)`, no hand-built
`FileChangeNotifier` and no second list naming the file the source already names. The debounce
is the library's default, which is the 300 ms the hand-wired one passed. Verified live: an
edit made in a text editor was picked up by the `modelrack4j-config-watcher` thread and logged
as `[strong] added` with no restart. A first poll immediately after the write still saw the old
configuration — that is the debounce, not a failure, and it is why a test cannot assert on this
without waiting.

**The writable layer comes from the registry.** `RackChatApi.create(registry, conversations)`
finds the highest-precedence `WritableConfigSource` among `registry.sources()` instead of
being handed one. The pair that could disagree — a registry and a layer it was never built
from — is no longer expressible.

**A save now drops the histories of what it removed.** This was a defect, and the reason is
worth keeping: **modelrack4j fires no reload listener for a `store`**, because it answers the
caller with the `ReloadChange` instead. `Main`'s listener therefore ran for an edit made
outside the page and never for one made in it, so a connection deleted through the editor kept
its chat histories — and a connection of that name added back later resumed a conversation
from before it was deleted. The endpoint now forgets what the returned change removed. The
test that pins it fails without the fix, which is how it was checked: remove `remembers`
through `/api/config`, add it back, and its next answer must report one message rather than
three.

**28 tests, green.** The editor's own paths were driven live as well: a store through
`/api/config` removed a connection, `/api/connections` lost it without a restart, and the
watcher that woke up afterwards published nothing — it re-read, found what was already live,
and stayed quiet, exactly as the reference says.

## M3.6 — The snapshot moved, and brought a method with it

**Status: Done.**

modelrack4j was rebuilt and reinstalled on 2026-09-07, three commits on from the one M3.4
built against. **The 28 tests were green against the new jar before anything was changed
here**, which is the first time ADR-0014's standing risk — an upstream `mvn install` moving
what RackChat compiles against, with no version to say so — was actually exercised.

One public method is new: `LlmRegistry.writableSources()`, the writable layers among
`sources()`, in the same order. RackChat now calls it instead of filtering `sources()` by
`instanceof` and casting. ADR-0015's decision is untouched — the registry is still what is
asked for the layer to write, rather than a reference carried beside it — and only the call
changed; the five lines it replaces were the same five the library had been shipping as its
own example, which is why upstream removed them from both sides.

The rest of what arrived is documentation, and two pieces of it describe RackChat's own
findings: the manual now has *The state you keep beside the registry*, which is the two-route
rule M3.5 hit as a defect, and its `LlmBundle`/`LlmConfig` records are no longer the `0.1.0`
shapes.

**One line of that guidance is deliberately not followed.** It suggests dropping state for
`change.updated()` as well as `removed()`, "if a changed configuration invalidates yours".
Here it does not: ADR-0012 decided that editing a block does not reshape a conversation
already in progress, so that a changed timeout does not silently discard a chat. A memory
built from the previous bundle keeps its policy until the conversation ends, and only
`removed()` is forgotten.

## M4 — A public repository

**Status: Done.**

The repository is public under Apache-2.0, decided in
[ADR-0016](../adr/0016-publish-the-repository-under-apache-2-0.md). `LICENSE` and `NOTICE` at
the root, licence, `scm` and developer metadata in `backend/pom.xml`, and `CONTRIBUTING.md`
asking for an issue before a pull request. The vendored Hyperapp keeps its own MIT licence
beside the file it covers, which is what `NOTICE` names.

**The history carried thirteen session-link trailers**, on every commit back to the root. They
point at a conversation only the owner can open, and publishing would have fixed them in place
permanently — the one part of going public that cannot be corrected afterwards. Rewriting them
out is cheap only while the repository is private, which is why it happened before the switch
rather than after. `Co-Authored-By` stayed: it names a role, not a private URL.

**CI exists for the first time.** `.github/workflows/build.yml` runs `mvn verify` on JDK 21 and
25, plus a third job with `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `GEMINI_API_KEY` and
`ZHIPU_API_KEY` set to the empty string. That job is the point of the exercise: "the tests need
no key and no network" has been asserted since M1 and true only because nobody had checked it
on a machine other than this one.

**Every job installs modelrack4j from source before it can build anything**, which is ADR-0014's
cost showing up somewhere new. It is also the clearest possible statement of when the step goes:
the day `0.2.0` is on Maven Central, the step and the paragraph in `docs/running-locally.md`
are deleted together, and leaving it would mean CI quietly testing against modelrack4j's `main`
rather than the release the build asks for.

**`modelrack4j.version` is now `0.2.0` rather than `0.2.0-SNAPSHOT`.** ADR-0014 already said
this was one property and not a decision to revisit. Taking it now, ahead of the publication,
means the coordinate stops moving under the build: an `mvn install` upstream no longer changes
what RackChat compiles against without a version saying so. 28 tests green offline against the
released jar.

**The screenshots are of the echo provider, on purpose.** A README picture of a chat application
is the first thing anybody looks at, and the honest version of it here is the fake provider
answering `saw 3: user=…|ai|user=…` — which happens to show the memory working, in the one way
it is visible from outside. They were taken with the configuration at `/tmp/rackchat/` rather
than under a home directory, because the configuration tab prints the path it is editing.
