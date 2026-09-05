# ADR-0005: Javalin as the backend web framework

- **Status:** Accepted
- **Date:** 2026-09-05
- **Supersedes:** —
- **Amends:** —

## Context

RackChat needs a backend that can serve a small HTTP API: chat with a selected LLM
connection (ideally streamed to the browser as it is generated), and read/write the
modelrack4j configuration through its `store()` API. The owner asked specifically for
something lighter than Spring, which was the first option discussed and rejected as too
heavy — too many concepts (auto-configuration, dependency injection conventions) for what
this project needs.

## Forces

- **Spring (Boot)** — full-featured, huge ecosystem, but brings a large surface of
  configuration and "magic" auto-wiring that this small project does not need, and that
  the owner explicitly wanted to avoid.
- **Micronaut / Quarkus** — lighter than Spring at runtime, but still bring their own
  dependency-injection model and build-time processing to learn; more than this project's
  scope justifies.
- **No framework at all** (JDK's own `com.sun.net.httpserver.HttpServer`, or Helidon Níma
  with virtual threads) — the minimal extreme, consistent with modelrack4j's own
  "plain Java, no framework" philosophy, but requires writing routing, JSON handling and
  streaming support by hand.
- **Javalin** — a thin layer over Jetty: routing, JSON, Server-Sent Events and WebSocket
  support out of the box, with everything explicit (no annotations, no auto-configuration).

## Decision

Use Javalin as the backend web framework. It gives RackChat request routing and streaming
(needed for the chat response) without introducing a dependency-injection framework or a
build-time processing step to learn.

## Consequences

RackChat's backend has one more runtime dependency than the "no framework" option would,
but avoids hand-rolling HTTP parsing, routing and Server-Sent Events. If a future need
outgrows Javalin (for example, a dependency-injection graph large enough that manual wiring
becomes unwieldy), that is a new ADR, not a silent swap.
