# ADR-0009: Stream the answer over Server-Sent Events, framed as JSON

- **Status:** Accepted
- **Date:** 2026-09-06
- **Supersedes:** —
- **Amends:** —

## Context

A chat answer arrives from the model a piece at a time, and modelrack4j exposes that as a
`StreamingChatModel` for any configuration block with `streaming = true`. The browser needs
those pieces as they arrive; the question is what carries them.

## Forces

- **WebSocket** is bidirectional and Javalin supports it, but nothing in RackChat needs the
  client-to-server direction while an answer is streaming: the question is already in the
  request. A WebSocket also brings its own connection lifecycle to manage on both ends.
- **Server-Sent Events** are one-directional by design, which is exactly the shape of the
  problem, and the browser side is `EventSource` with no library. The cost is that
  `EventSource` only issues GET requests, so the question travels as a query parameter
  rather than in a request body, and that it reconnects on its own when the connection drops.
- **Polling** would avoid streaming altogether, at the price of the thing the feature is for.

Two details forced themselves during implementation and are part of this decision:

- SSE frames are line-based and strip a single space after `data:`. Sending a raw token
  would silently mangle leading whitespace and any newline inside it.
- Javalin's SSE handler does nothing at all unless the request carries
  `Accept: text/event-stream` — the response is an empty `200 text/plain` and the handler
  never runs.

## Decision

The chat endpoint is `GET /api/chat?connection=<name>&message=<text>`, served as Server-Sent
Events. Every frame's payload is JSON, not bare text: `token` frames carry `{"text": "..."}`
and the final `done` frame carries `{"error": null | "..."}`. The server closes the stream
after `done`, and the browser closes its `EventSource` on receiving it.

A connection without a streaming model is not a separate endpoint: the same handler falls
back to one blocking call and sends the whole answer as a single `token` frame.

## Consequences

The question is in the URL, so it appears in server logs and has a length limit — acceptable
for a personal tool, and the thing to revisit first if either becomes a problem. Closing the
`EventSource` in the `done` handler is load-bearing: without it the browser reconnects when
the server hangs up and asks the model the same question again, at cost. JSON framing is
also load-bearing and must not be "simplified" back to plain text — it is what makes
`" the"` and a multi-line answer survive the transport. Any hand-written client (a test, a
`curl` probe) has to send the `Accept` header; a browser sends it automatically, so this only
ever bites off the browser path.
