# ADR-0006: Hyperapp as the frontend library

- **Status:** Accepted
- **Date:** 2026-09-05
- **Supersedes:** —
- **Amends:** —

## Context

The owner asked for something in the style of Elm — a single, immutable application state,
a pure function producing the next state from a message, a pure function rendering the view
from the state — for the chat page and its configuration editor.

Two real paths were considered: staying in the JavaScript/TypeScript ecosystem with a
library that follows Elm's architecture, or moving the frontend itself to Scala.js, which
has genuine Elm-architecture libraries (Tyrian, actively maintained) and would let the whole
stack — backend and frontend — share one language and even cross-compiled data models.

## Forces

- **Full Elm** (the language) — the strongest guarantee (no runtime exceptions), but a
  second language and toolchain to learn and maintain next to the backend.
- **Scala.js + Tyrian** — keeps the whole stack in one language and opens the door to
  sharing the configuration model between backend and frontend, but doubles the build
  tooling (a JVM toolchain and a Scala.js one, sbt instead of only the backend's build
  tool) for a project that has not yet written a line of code.
- **Redux-style JavaScript** — mainstream and well-documented, but the reducer pattern
  brings more boilerplate than a small chat page needs.
- **Hyperapp** — a small (about 2 KB) JavaScript library built directly on Elm's
  architecture: one state, a pure view function, actions that produce a new state. No
  build step is required to use it.

The owner chose to stay on the JavaScript side for now and revisit Scala.js later if it
turns out to matter — this ADR records the immediate choice, not a rejection of Scala.js.

## Decision

Use Hyperapp for the frontend.

## Consequences

The frontend is plain JavaScript (or TypeScript, a separate and still-open choice), not
Scala.js — so there is no shared model between backend and frontend yet; the configuration
schema is duplicated (once in the backend's modelrack4j integration, once in whatever the
frontend's editor expects) until or unless that changes. Revisiting Scala.js later is a new
ADR, not a reopening of this one, per ADR-0001.
