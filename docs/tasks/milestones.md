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

**Status: Not started.** Sketch, not a committed scope.

The other half of the original idea: edit the configuration in the browser and save it
through modelrack4j's `store()`, which validates the whole configuration before it writes
and puts the previous snapshot back if the write fails. Two things must be decided first —
where the configuration file lives when the application is not run from a checkout, and
whether the editor is a text area over the raw HOCON or a form over its fields. Neither is
settled; see `open-decisions.md`.
