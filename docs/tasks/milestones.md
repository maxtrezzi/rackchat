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

**Status: Not started.** This is a rough sketch, not a committed scope — refine it into
concrete tasks before starting the work, and update this entry (or split it into several
smaller items) once that refinement happens.

Roughly: a Javalin backend that loads a modelrack4j `LlmRegistry` from a configuration
file, exposes an endpoint to send a chat message to a named connection and stream the
response back, and a minimal Hyperapp page that can send a message and show the streamed
reply. The configuration editor and the connection selector UI are likely a separate
milestone (M2) rather than part of the first runnable slice — confirm that split before
committing to it.
